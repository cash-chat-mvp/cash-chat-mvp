package com.wnl.cashchat.api.domain.chat.service

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.chat.persistence.entity.ChatMessage
import com.wnl.cashchat.api.domain.chat.persistence.entity.Conversation
import com.wnl.cashchat.api.domain.chat.persistence.entity.MessageRole
import com.wnl.cashchat.api.domain.chat.persistence.entity.MessageStatus
import com.wnl.cashchat.api.domain.chat.persistence.repository.ChatMessageRepository
import com.wnl.cashchat.api.domain.chat.persistence.repository.ConversationRepository
import com.wnl.cashchat.api.domain.point.exception.InsufficientPointsException
import com.wnl.cashchat.api.domain.point.service.UserPointService
import com.wnl.cashchat.api.domain.chat.service.llm.LlmMessage
import com.wnl.cashchat.api.domain.chat.service.llm.LlmMessageRole
import com.wnl.cashchat.api.domain.chat.service.llm.LlmProvider
import com.wnl.cashchat.api.domain.user.persistence.entity.Role
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.util.Optional

class ChatServiceTest : FunSpec() {
    private lateinit var conversationRepository: ConversationRepository
    private lateinit var chatMessageRepository: ChatMessageRepository
    private lateinit var userPointService: UserPointService
    private lateinit var llmProvider: LlmProvider
    private lateinit var chatService: ChatService
    private lateinit var savedMessages: MutableList<SavedMessageSnapshot>
    private lateinit var savedMessageEntities: MutableMap<Long, ChatMessage>
    private var nextMessageId = 100L

    init {
        beforeTest {
            conversationRepository = mock()
            chatMessageRepository = mock()
            userPointService = mock()
            llmProvider = mock()
            savedMessages = mutableListOf()
            savedMessageEntities = mutableMapOf()
            nextMessageId = 100L
            chatService = ChatService(
                conversationRepository = conversationRepository,
                chatMessageRepository = chatMessageRepository,
                userPointService = userPointService,
                llmProvider = llmProvider,
                transactionManager = NoOpTransactionManager(),
            )
        }

        test("stream rejects conversations owned by another user") {
            val owner = User(id = 2L, role = Role.MEMBER, provider = AuthProviderType.NONE, name = "owner")
            val conversation = Conversation(id = 1L, user = owner, title = null)

            whenever(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation))

            val error = shouldThrow<IllegalArgumentException> {
                chatService.stream(userId = 99L, conversationId = 1L, content = "hello").blockLast()
            }

            error.message shouldBe "Conversation does not belong to user"
        }

        test("stream rejects insufficient point balance before persisting messages") {
            val conversation = conversation(ownerId = 1L)

            whenever(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation))
            whenever(userPointService.hasEnoughBalance(1L)).thenReturn(false)

            shouldThrow<InsufficientPointsException> {
                chatService.stream(userId = 1L, conversationId = 1L, content = "hello")
            }

            verify(chatMessageRepository, never()).save(any())
            verify(llmProvider, never()).stream(any())
        }

        test("stream sends only completed history plus the current user message to the provider") {
            val conversation = conversation(ownerId = 1L)
            val history = listOf(
                ChatMessage(
                    id = 10L,
                    conversation = conversation,
                    role = MessageRole.SYSTEM,
                    content = "system prompt",
                    status = MessageStatus.COMPLETED
                ),
                ChatMessage(
                    id = 11L,
                    conversation = conversation,
                    role = MessageRole.USER,
                    content = "previous question",
                    status = MessageStatus.COMPLETED
                ),
                ChatMessage(
                    id = 12L,
                    conversation = conversation,
                    role = MessageRole.ASSISTANT,
                    content = "failed answer",
                    status = MessageStatus.FAILED
                ),
                ChatMessage(
                    id = 13L,
                    conversation = conversation,
                    role = MessageRole.ASSISTANT,
                    content = "partial answer",
                    status = MessageStatus.STREAMING
                ),
            )

            whenever(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation))
            whenever(userPointService.hasEnoughBalance(1L)).thenReturn(true)
            whenever(chatMessageRepository.findAllByConversationIdOrderByCreatedAtAsc(1L)).thenReturn(history)
            stubMessagePersistence()
            whenever(llmProvider.stream(any())).thenReturn(Flux.just("hi there"))

            StepVerifier.create(chatService.stream(userId = 1L, conversationId = 1L, content = "hello"))
                .expectNext("hi there")
                .verifyComplete()

            verify(llmProvider).stream(
                argThat<List<LlmMessage>> {
                    this == listOf(
                        LlmMessage(LlmMessageRole.SYSTEM, "system prompt"),
                        LlmMessage(LlmMessageRole.USER, "previous question"),
                        LlmMessage(LlmMessageRole.USER, "hello"),
                    )
                }
            )
        }

        test("stream marks the assistant message completed when streaming finishes") {
            val conversation = conversation(ownerId = 1L)

            stubConversation(conversation)
            whenever(llmProvider.stream(any())).thenReturn(Flux.just("hi", " there"))

            StepVerifier.create(chatService.stream(userId = 1L, conversationId = 1L, content = "hello"))
                .expectNext("hi", " there")
                .verifyComplete()

            savedMessages shouldContain SavedMessageSnapshot(
                role = MessageRole.ASSISTANT,
                status = MessageStatus.COMPLETED,
                content = "hi there",
            )
        }

        test("stream marks the assistant message failed when the provider errors") {
            val conversation = conversation(ownerId = 1L)

            stubConversation(conversation)
            whenever(llmProvider.stream(any())).thenReturn(Flux.error(IllegalStateException("boom")))

            StepVerifier.create(chatService.stream(userId = 1L, conversationId = 1L, content = "hello"))
                .expectErrorMessage("boom")
                .verify()

            savedMessages shouldContain SavedMessageSnapshot(
                role = MessageRole.ASSISTANT,
                status = MessageStatus.FAILED,
                content = "",
            )
        }

        test("stream marks the assistant message failed when cancelled before any chunk arrives") {
            val conversation = conversation(ownerId = 1L)

            stubConversation(conversation)
            whenever(llmProvider.stream(any())).thenReturn(Flux.never())

            StepVerifier.create(chatService.stream(userId = 1L, conversationId = 1L, content = "hello"))
                .thenCancel()
                .verify()

            savedMessages shouldContain SavedMessageSnapshot(
                role = MessageRole.ASSISTANT,
                status = MessageStatus.FAILED,
                content = "",
            )
        }

        test("stream keeps partial assistant content when cancelled after a chunk arrives") {
            val conversation = conversation(ownerId = 1L)

            stubConversation(conversation)
            whenever(llmProvider.stream(any())).thenReturn(Flux.just("hi").concatWith(Flux.never()))

            StepVerifier.create(chatService.stream(userId = 1L, conversationId = 1L, content = "hello"))
                .expectNext("hi")
                .thenCancel()
                .verify()

            savedMessages shouldContain SavedMessageSnapshot(
                role = MessageRole.ASSISTANT,
                status = MessageStatus.COMPLETED,
                content = "hi",
            )
        }
    }

    private fun stubConversation(conversation: Conversation) {
        whenever(conversationRepository.findById(conversation.id)).thenReturn(Optional.of(conversation))
        whenever(userPointService.hasEnoughBalance(conversation.user.id)).thenReturn(true)
        whenever(chatMessageRepository.findAllByConversationIdOrderByCreatedAtAsc(conversation.id)).thenReturn(emptyList())
        stubMessagePersistence()
    }

    private fun conversation(ownerId: Long): Conversation {
        val owner = User(id = ownerId, role = Role.MEMBER, provider = AuthProviderType.NONE, name = "owner")
        return Conversation(id = 1L, user = owner, title = null)
    }

    private fun stubMessagePersistence() {
        whenever(chatMessageRepository.save(any<ChatMessage>())).thenAnswer { invocation ->
            val message = invocation.getArgument<ChatMessage>(0)
            savedMessages += SavedMessageSnapshot(
                role = message.role,
                status = message.status,
                content = message.content,
            )

            if (message.id > 0) {
                savedMessageEntities[message.id] = message
                message
            } else {
                persistNewMessage(message)
            }
        }

        whenever(chatMessageRepository.findById(any())).thenAnswer { invocation ->
            Optional.ofNullable(savedMessageEntities[invocation.getArgument<Long>(0)])
        }
    }

    private fun persistNewMessage(message: ChatMessage): ChatMessage {
        val persisted = ChatMessage(
            id = nextMessageId++,
            conversation = message.conversation,
            role = message.role,
            content = message.content,
            status = message.status,
            model = message.model,
        )
        savedMessageEntities[persisted.id] = persisted
        return persisted
    }

    private data class SavedMessageSnapshot(
        val role: MessageRole,
        val status: MessageStatus,
        val content: String,
    )

    private class NoOpTransactionManager : PlatformTransactionManager {
        override fun getTransaction(definition: TransactionDefinition?): TransactionStatus = SimpleTransactionStatus()

        override fun commit(status: TransactionStatus) = Unit

        override fun rollback(status: TransactionStatus) = Unit
    }
}
