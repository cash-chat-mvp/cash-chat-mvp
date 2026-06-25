package com.wnl.cashchat.api.domain.chat.service

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.chat.exception.ConversationAccessDeniedException
import com.wnl.cashchat.api.domain.chat.exception.ConversationNotFoundException
import com.wnl.cashchat.api.domain.chat.persistence.entity.ChatMessage
import com.wnl.cashchat.api.domain.chat.persistence.entity.Conversation
import com.wnl.cashchat.api.domain.chat.persistence.entity.MessageRole
import com.wnl.cashchat.api.domain.chat.persistence.entity.MessageStatus
import com.wnl.cashchat.api.domain.chat.persistence.repository.ChatMessageRepository
import com.wnl.cashchat.api.domain.chat.persistence.repository.ConversationRepository
import com.wnl.cashchat.api.domain.chat.service.llm.LlmMessage
import com.wnl.cashchat.api.domain.chat.service.llm.LlmMessageRole
import com.wnl.cashchat.api.domain.chat.service.llm.LlmProvider
import com.wnl.cashchat.api.domain.chat.service.routing.ChatModelRouter
import com.wnl.cashchat.api.domain.chat.service.routing.ModelTier
import com.wnl.cashchat.api.domain.energy.exception.InsufficientEnergyException
import com.wnl.cashchat.api.domain.user.persistence.entity.Role
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import com.wnl.cashchat.api.domain.user.persistence.repository.UserRepository
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
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
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

class ChatServiceTest : FunSpec() {
    private lateinit var conversationRepository: ConversationRepository
    private lateinit var chatMessageRepository: ChatMessageRepository
    private lateinit var userRepository: UserRepository
    private lateinit var llmProvider: LlmProvider
    private lateinit var chatModelRouter: ChatModelRouter
    private lateinit var chatRewardService: ChatRewardService
    private lateinit var chatService: ChatService
    private lateinit var savedMessages: MutableList<SavedMessageSnapshot>
    private lateinit var savedMessageEntities: MutableMap<Long, ChatMessage>
    private var nextMessageId = 100L

    init {
        beforeTest {
            conversationRepository = mock()
            chatMessageRepository = mock()
            userRepository = mock()
            llmProvider = mock()
            chatModelRouter = mock()
            chatRewardService = mock()
            savedMessages = mutableListOf()
            savedMessageEntities = mutableMapOf()
            nextMessageId = 100L
            chatService = ChatService(
                conversationRepository = conversationRepository,
                chatMessageRepository = chatMessageRepository,
                userRepository = userRepository,
                llmProvider = llmProvider,
                chatModelRouter = chatModelRouter,
                chatRewardService = chatRewardService,
                transactionManager = NoOpTransactionManager(),
            )
        }

        test("createConversation saves a conversation for the authenticated user") {
            val user = User(id = 1L, role = Role.MEMBER, provider = AuthProviderType.NONE, name = "owner")
            val createdAt = java.time.Instant.parse("2026-05-16T00:00:00Z")

            whenever(userRepository.findById(1L)).thenReturn(Optional.of(user))
            whenever(conversationRepository.save(any<Conversation>())).thenAnswer { invocation ->
                val conversation = invocation.getArgument<Conversation>(0)
                Conversation(id = 7L, user = conversation.user, title = conversation.title).also {
                    it.createdAt = createdAt
                    it.updatedAt = createdAt
                }
            }

            val response = chatService.createConversation(userId = 1L, title = "영어 공부 방법")

            response.conversationId shouldBe 7L
            response.title shouldBe "영어 공부 방법"
            response.createdAt shouldBe createdAt
            verify(conversationRepository).save(argThat { user.id == 1L && title == "영어 공부 방법" })
        }

        test("listConversations returns summaries with latest message preview using one batch lookup") {
            val conversation = conversation(ownerId = 1L)
            conversation.title = "영어 공부 방법"
            conversation.updatedAt = java.time.Instant.parse("2026-05-16T00:10:00Z")
            val latestMessage = ChatMessage(
                id = 20L,
                conversation = conversation,
                role = MessageRole.ASSISTANT,
                content = "매일 짧게 공부하세요",
                status = MessageStatus.COMPLETED,
            )

            whenever(conversationRepository.findAllByUserIdOrderByUpdatedAtDesc(1L)).thenReturn(listOf(conversation))
            whenever(chatMessageRepository.findLatestByConversationIds(listOf(1L))).thenReturn(listOf(latestMessage))

            val summaries = chatService.listConversations(userId = 1L)

            summaries shouldHaveSize 1
            summaries[0].conversationId shouldBe 1L
            summaries[0].title shouldBe "영어 공부 방법"
            summaries[0].lastMessage shouldBe "매일 짧게 공부하세요"
            verify(chatMessageRepository).findLatestByConversationIds(listOf(1L))
            verify(chatMessageRepository, never()).findTopByConversationIdOrderByCreatedAtDesc(any())
        }

        test("getMessages returns only messages from a conversation owned by the user") {
            val conversation = conversation(ownerId = 1L)
            val createdAt = java.time.Instant.parse("2026-05-16T00:01:00Z")
            val message = ChatMessage(
                id = 10L,
                conversation = conversation,
                role = MessageRole.USER,
                content = "영어 공부 방법",
                status = MessageStatus.COMPLETED,
            ).also { it.createdAt = createdAt }

            whenever(conversationRepository.findByIdAndUserId(1L, 1L)).thenReturn(conversation)
            whenever(chatMessageRepository.findAllByConversationIdOrderByCreatedAtAsc(1L)).thenReturn(listOf(message))

            val messages = chatService.getMessages(userId = 1L, conversationId = 1L)

            messages shouldHaveSize 1
            messages[0].messageId shouldBe 10L
            messages[0].role shouldBe "USER"
            messages[0].content shouldBe "영어 공부 방법"
            messages[0].status shouldBe "COMPLETED"
            messages[0].createdAt shouldBe createdAt
        }

        test("getMessages rejects a conversation not owned by the user") {
            whenever(conversationRepository.findByIdAndUserId(1L, 99L)).thenReturn(null)

            shouldThrow<ConversationNotFoundException> {
                chatService.getMessages(userId = 99L, conversationId = 1L)
            }

            verify(chatMessageRepository, never()).findAllByConversationIdOrderByCreatedAtAsc(any())
        }

        test("stream rejects conversations owned by another user") {
            whenever(conversationRepository.findByIdAndUserId(1L, 99L)).thenReturn(null)

            shouldThrow<ConversationNotFoundException> {
                chatService.stream(userId = 99L, conversationId = 1L, content = "hello").blockLast()
            }
        }

        test("stream calls chatModelRouter.routeAndConsume on the normal path") {
            val conversation = conversation(ownerId = 1L)

            stubConversation(conversation)
            whenever(llmProvider.stream(any())).thenReturn(Flux.just("ok"))

            StepVerifier.create(chatService.stream(userId = 1L, conversationId = 1L, content = "hello"))
                .expectNext("ok")
                .verifyComplete()

            verify(chatModelRouter).routeAndConsume(eq(1L), any())
        }

        test("stream does not call llmProvider when energy gate (InsufficientEnergyException) triggers") {
            val conversation = conversation(ownerId = 1L)

            whenever(conversationRepository.findByIdAndUserId(1L, 1L)).thenReturn(conversation)
            whenever(chatModelRouter.routeAndConsume(eq(1L), any()))
                .thenThrow(InsufficientEnergyException())

            shouldThrow<InsufficientEnergyException> {
                chatService.stream(userId = 1L, conversationId = 1L, content = "hello")
            }

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

            whenever(conversationRepository.findByIdAndUserId(1L, 1L)).thenReturn(conversation)
            whenever(chatModelRouter.routeAndConsume(eq(1L), any())).thenReturn(ModelTier.NANO)
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

            // 취소 시 확정·정산은 boundedElastic 으로 오프로딩되므로 비동기 완료를 기다린다.
            eventually(2.seconds) {
                savedMessages shouldContain SavedMessageSnapshot(
                    role = MessageRole.ASSISTANT,
                    status = MessageStatus.FAILED,
                    content = "",
                )
            }
        }

        test("stream keeps partial assistant content when cancelled after a chunk arrives") {
            val conversation = conversation(ownerId = 1L)

            stubConversation(conversation)
            whenever(llmProvider.stream(any())).thenReturn(Flux.just("hi").concatWith(Flux.never()))

            StepVerifier.create(chatService.stream(userId = 1L, conversationId = 1L, content = "hello"))
                .expectNext("hi")
                .thenCancel()
                .verify()

            // 취소 시 확정·정산은 boundedElastic 으로 오프로딩되므로 비동기 완료를 기다린다.
            eventually(2.seconds) {
                savedMessages shouldContain SavedMessageSnapshot(
                    role = MessageRole.ASSISTANT,
                    status = MessageStatus.COMPLETED,
                    content = "hi",
                )
            }
        }

        test("stream settles chat reward when streaming completes normally") {
            val conversation = conversation(ownerId = 1L)

            stubConversation(conversation)
            whenever(llmProvider.stream(any())).thenReturn(Flux.just("hi", " there"))

            StepVerifier.create(chatService.stream(userId = 1L, conversationId = 1L, content = "hello"))
                .expectNext("hi", " there")
                .verifyComplete()

            verify(chatRewardService).settle(eq(1L), any())
            verify(chatRewardService, never()).refund(any())
        }

        test("stream refunds reserved energy when the provider errors") {
            val conversation = conversation(ownerId = 1L)

            stubConversation(conversation)
            whenever(llmProvider.stream(any())).thenReturn(Flux.error(IllegalStateException("boom")))

            StepVerifier.create(chatService.stream(userId = 1L, conversationId = 1L, content = "hello"))
                .expectErrorMessage("boom")
                .verify()

            verify(chatRewardService).refund(1L)
            verify(chatRewardService, never()).settle(any(), any())
        }

        test("stream neither settles nor refunds when the energy gate triggers") {
            val conversation = conversation(ownerId = 1L)

            whenever(conversationRepository.findByIdAndUserId(1L, 1L)).thenReturn(conversation)
            whenever(chatModelRouter.routeAndConsume(eq(1L), any()))
                .thenThrow(InsufficientEnergyException())

            shouldThrow<InsufficientEnergyException> {
                chatService.stream(userId = 1L, conversationId = 1L, content = "hello")
            }

            verify(chatRewardService, never()).settle(any(), any())
            verify(chatRewardService, never()).refund(any())
        }

        test("getHistory returns owned conversation messages ordered by creation time") {
            val conversationUuid = UUID.fromString("0c4fe408-6d7c-4bd9-b0f8-5fdbe2a6a6e8")
            val conversation = conversation(ownerId = 1L, uuid = conversationUuid)
            val first = ChatMessage(
                id = 10L,
                conversation = conversation,
                role = MessageRole.USER,
                content = "hello",
                status = MessageStatus.COMPLETED
            )
            val second = ChatMessage(
                id = 11L,
                conversation = conversation,
                role = MessageRole.ASSISTANT,
                content = "hi there",
                status = MessageStatus.COMPLETED,
                model = "gpt-4o-mini"
            )

            whenever(conversationRepository.findByUuid(conversationUuid)).thenReturn(conversation)
            whenever(chatMessageRepository.findAllByConversationIdOrderByCreatedAtAsc(1L))
                .thenReturn(listOf(first, second))

            val history = chatService.getHistory(userId = 1L, conversationUuid = conversationUuid)

            history.conversationUuid shouldBe conversationUuid
            history.messages shouldBe listOf(first, second)
            verify(chatMessageRepository).findAllByConversationIdOrderByCreatedAtAsc(1L)
        }

        test("getHistory rejects conversations owned by another user") {
            val conversationUuid = UUID.fromString("7a4e58c0-e8dc-4f26-9b86-fdc50d03d49f")
            val conversation = conversation(ownerId = 2L, uuid = conversationUuid)

            whenever(conversationRepository.findByUuid(conversationUuid)).thenReturn(conversation)

            shouldThrow<ConversationAccessDeniedException> {
                chatService.getHistory(userId = 1L, conversationUuid = conversationUuid)
            }

            verify(chatMessageRepository, never()).findAllByConversationIdOrderByCreatedAtAsc(any())
        }

        test("getHistory rejects unknown conversation uuid") {
            val conversationUuid = UUID.fromString("bd1d0ebf-599c-4a11-a582-5a8fbb716a5c")

            whenever(conversationRepository.findByUuid(conversationUuid)).thenReturn(null)

            shouldThrow<ConversationNotFoundException> {
                chatService.getHistory(userId = 1L, conversationUuid = conversationUuid)
            }

            verify(chatMessageRepository, never()).findAllByConversationIdOrderByCreatedAtAsc(any())
        }
    }

    private fun stubConversation(conversation: Conversation) {
        whenever(conversationRepository.findByIdAndUserId(conversation.id, conversation.user.id)).thenReturn(conversation)
        whenever(chatModelRouter.routeAndConsume(eq(conversation.user.id), any())).thenReturn(ModelTier.NANO)
        whenever(chatMessageRepository.findAllByConversationIdOrderByCreatedAtAsc(conversation.id)).thenReturn(emptyList())
        stubMessagePersistence()
    }

    private fun conversation(ownerId: Long, uuid: UUID = UUID.randomUUID()): Conversation {
        val owner = User(id = ownerId, role = Role.MEMBER, provider = AuthProviderType.NONE, name = "owner")
        return Conversation(id = 1L, uuid = uuid, user = owner, title = null)
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
