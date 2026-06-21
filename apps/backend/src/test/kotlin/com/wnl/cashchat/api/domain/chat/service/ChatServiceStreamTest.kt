package com.wnl.cashchat.api.domain.chat.service

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.chat.exception.ConversationNotFoundException
import com.wnl.cashchat.api.domain.chat.persistence.entity.ChatMessage
import com.wnl.cashchat.api.domain.chat.persistence.entity.Conversation
import com.wnl.cashchat.api.domain.chat.persistence.entity.MessageRole
import com.wnl.cashchat.api.domain.chat.persistence.entity.MessageStatus
import com.wnl.cashchat.api.domain.chat.persistence.entity.SettlementStatus
import com.wnl.cashchat.api.domain.chat.persistence.repository.ChatMessageRepository
import com.wnl.cashchat.api.domain.chat.persistence.repository.ConversationRepository
import com.wnl.cashchat.api.domain.chat.service.llm.LlmProvider
import com.wnl.cashchat.api.domain.economy.exception.FeatureDisabledException
import com.wnl.cashchat.api.domain.economy.properties.EconomyProperties
import com.wnl.cashchat.api.domain.user.persistence.entity.Role
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import com.wnl.cashchat.api.domain.user.persistence.repository.UserRepository
import io.kotest.core.spec.style.FunSpec
import org.mockito.kotlin.any
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
import java.time.Instant
import java.util.Optional

class ChatServiceStreamTest : FunSpec() {

    private lateinit var conversationRepository: ConversationRepository
    private lateinit var chatMessageRepository: ChatMessageRepository
    private lateinit var userRepository: UserRepository
    private lateinit var llmProvider: LlmProvider
    private lateinit var settlementService: ChatRewardSettlementService
    private lateinit var chatService: ChatService

    private var nextMessageId = 100L
    private val savedMessageEntities = mutableMapOf<Long, ChatMessage>()

    init {
        beforeTest {
            conversationRepository = mock()
            chatMessageRepository = mock()
            userRepository = mock()
            llmProvider = mock()
            settlementService = mock()
            nextMessageId = 100L
            savedMessageEntities.clear()
        }

        test("stream throws FeatureDisabledException when rewardChatEnabled is false") {
            val props = EconomyProperties(rewardChatEnabled = false)
            chatService = buildService(props)

            val conversation = conversation(ownerId = 1L)
            whenever(conversationRepository.findByIdAndUserId(1L, 1L)).thenReturn(conversation)

            StepVerifier.create(
                chatService.stream(userId = 1L, conversationId = 1L, messageId = "msg-001", content = "hello")
            )
                .verifyError(FeatureDisabledException::class.java)

            verify(settlementService, never()).beginReservation(any(), any(), any())
            verify(llmProvider, never()).stream(any())
        }

        test("stream produces Meta, Delta, RewardSettled, Done in order on success") {
            val props = EconomyProperties(rewardChatEnabled = true)
            chatService = buildService(props)

            val conversation = conversation(ownerId = 1L)
            whenever(conversationRepository.findByIdAndUserId(1L, 1L)).thenReturn(conversation)
            whenever(chatMessageRepository.findAllByConversationIdOrderByCreatedAtAsc(1L)).thenReturn(emptyList())
            stubMessagePersistence()
            whenever(settlementService.beginReservation(1L, 1L, "msg-001")).thenReturn(42L)
            whenever(llmProvider.stream(any())).thenReturn(Flux.just("A", "B"))
            val settlementResult = SettlementResult(
                messageId = "msg-001",
                status = SettlementStatus.SETTLED,
                energyDelta = -1L,
                pendingPtDelta = 1L,
                evolutionExpDelta = 1L,
                energyBalance = 9L,
                pendingCashablePt = 1L,
                evolutionExp = 1L,
                settledAt = Instant.now(),
            )
            whenever(settlementService.settle(any(), any(), any())).thenReturn(settlementResult)

            StepVerifier.create(
                chatService.stream(userId = 1L, conversationId = 1L, messageId = "msg-001", content = "hello")
            )
                .expectNextMatches { it is ChatStreamEvent.Meta && it.messageId == "msg-001" && it.energyReserved == 1L }
                .expectNextMatches { it is ChatStreamEvent.Delta && it.text == "A" }
                .expectNextMatches { it is ChatStreamEvent.Delta && it.text == "B" }
                .expectNextMatches { it is ChatStreamEvent.RewardSettled && it.result == settlementResult }
                .expectNextMatches { it is ChatStreamEvent.Done && it.finishReason == "STOP" }
                .verifyComplete()

            verify(settlementService).settle(any(), any(), any())
            verify(settlementService, never()).refund(any(), any(), any())
        }

        test("stream calls refund and propagates error when llm provider errors") {
            val props = EconomyProperties(rewardChatEnabled = true)
            chatService = buildService(props)

            val conversation = conversation(ownerId = 1L)
            whenever(conversationRepository.findByIdAndUserId(1L, 1L)).thenReturn(conversation)
            whenever(chatMessageRepository.findAllByConversationIdOrderByCreatedAtAsc(1L)).thenReturn(emptyList())
            stubMessagePersistence()
            whenever(settlementService.beginReservation(1L, 1L, "msg-001")).thenReturn(42L)
            whenever(llmProvider.stream(any())).thenReturn(Flux.error(RuntimeException("boom")))

            StepVerifier.create(
                chatService.stream(userId = 1L, conversationId = 1L, messageId = "msg-001", content = "hello")
            )
                .expectNextMatches { it is ChatStreamEvent.Meta }  // Meta emitted before llm error
                .expectError(RuntimeException::class.java)
                .verify()

            verify(settlementService).refund(any(), any(), any())
            verify(settlementService, never()).settle(any(), any(), any())
        }

        test("stream throws ConversationNotFoundException for unknown conversation") {
            val props = EconomyProperties(rewardChatEnabled = true)
            chatService = buildService(props)

            whenever(conversationRepository.findByIdAndUserId(1L, 99L)).thenReturn(null)

            StepVerifier.create(
                chatService.stream(userId = 99L, conversationId = 1L, messageId = "msg-001", content = "hello")
            )
                .verifyError(ConversationNotFoundException::class.java)
        }
    }

    private fun buildService(props: EconomyProperties): ChatService =
        ChatService(
            conversationRepository = conversationRepository,
            chatMessageRepository = chatMessageRepository,
            userRepository = userRepository,
            settlementService = settlementService,
            economyProperties = props,
            llmProvider = llmProvider,
            transactionManager = NoOpTransactionManager(),
        )

    private fun conversation(ownerId: Long): Conversation {
        val owner = User(id = ownerId, role = Role.MEMBER, provider = AuthProviderType.NONE, name = "owner")
        return Conversation(id = 1L, user = owner, title = null)
    }

    private fun stubMessagePersistence() {
        whenever(chatMessageRepository.save(any<ChatMessage>())).thenAnswer { invocation ->
            val message = invocation.getArgument<ChatMessage>(0)
            if (message.id > 0) {
                savedMessageEntities[message.id] = message
                message
            } else {
                val persisted = ChatMessage(
                    id = nextMessageId++,
                    conversation = message.conversation,
                    role = message.role,
                    content = message.content,
                    status = message.status,
                )
                savedMessageEntities[persisted.id] = persisted
                persisted
            }
        }
        whenever(chatMessageRepository.findById(any())).thenAnswer { invocation ->
            Optional.ofNullable(savedMessageEntities[invocation.getArgument<Long>(0)])
        }
    }

    private class NoOpTransactionManager : PlatformTransactionManager {
        override fun getTransaction(definition: TransactionDefinition?): TransactionStatus = SimpleTransactionStatus()
        override fun commit(status: TransactionStatus) = Unit
        override fun rollback(status: TransactionStatus) = Unit
    }
}
