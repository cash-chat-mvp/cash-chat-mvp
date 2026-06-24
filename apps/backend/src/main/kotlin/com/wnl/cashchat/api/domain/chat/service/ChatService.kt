package com.wnl.cashchat.api.domain.chat.service

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
import com.wnl.cashchat.api.domain.chat.web.response.ChatMessageResponse
import com.wnl.cashchat.api.domain.chat.web.response.ConversationResponse
import com.wnl.cashchat.api.domain.chat.web.response.ConversationSummaryResponse
import com.wnl.cashchat.api.domain.user.persistence.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import reactor.core.publisher.Flux
import reactor.core.publisher.SignalType
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Coordinates persistence and provider streaming for chat conversations.
 */
@Service
class ChatService(
    private val conversationRepository: ConversationRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val userRepository: UserRepository,
    private val llmProvider: LlmProvider,
    private val chatModelRouter: ChatModelRouter,
    private val chatRewardService: ChatRewardService,
    transactionManager: PlatformTransactionManager,
) {
    private val log = LoggerFactory.getLogger(ChatService::class.java)
    private val transactionTemplate = TransactionTemplate(transactionManager)

    fun createConversation(userId: Long, title: String?): ConversationResponse {
        val conversation = transactionTemplate.execute {
            val user = userRepository.findById(userId)
                .orElseThrow { IllegalArgumentException("User not found") }
            conversationRepository.save(
                Conversation(
                    user = user,
                    title = title.normalizedTitle()
                )
            )
        } ?: error("Failed to create conversation")

        return conversation.toResponse()
    }

    fun listConversations(userId: Long): List<ConversationSummaryResponse> {
        val conversations = conversationRepository.findAllByUserIdOrderByUpdatedAtDesc(userId)
        val conversationIds = conversations.map { it.id }
        val latestMessagesByConversationId = if (conversationIds.isEmpty()) {
            emptyMap()
        } else {
            chatMessageRepository.findLatestByConversationIds(conversationIds)
                .associateBy { it.conversation.id }
        }

        return conversations.map { conversation ->
            val latestMessage = latestMessagesByConversationId[conversation.id]
            ConversationSummaryResponse(
                conversationId = conversation.id,
                title = conversation.displayTitle(),
                lastMessage = latestMessage?.content,
                createdAt = conversation.createdAt,
                updatedAt = conversation.updatedAt,
            )
        }
    }

    fun getMessages(userId: Long, conversationId: Long): List<ChatMessageResponse> {
        conversationRepository.findByIdAndUserId(conversationId, userId)
            ?: throw ConversationNotFoundException(conversationId)

        return chatMessageRepository.findAllByConversationIdOrderByCreatedAtAsc(conversationId)
            .map { it.toResponse() }
    }

    /**
     * Streams an assistant response while persisting the user input and final assistant state.
     *
     * Economic loop (CC-340): routeAndConsume runs exactly once inside the setup transaction,
     * consuming 밥(energy) and routing to the appropriate model tier.
     * InsufficientEnergyException is the sole gate — it propagates up and is mapped to 409 by ChatExceptionHandler.
     */
    fun stream(userId: Long, conversationId: Long, content: String): Flux<String> {
        val today = LocalDate.now()
        val streamContext = transactionTemplate.execute {
            val conversation = conversationRepository.findByIdAndUserId(conversationId, userId)
                ?: throw ConversationNotFoundException(conversationId)

            // Economic gate + routing decision (밥 차감 → 풀 적립 → 티어 결정).
            // InsufficientEnergyException propagates; no LLM call occurs.
            val modelTier = chatModelRouter.routeAndConsume(userId, today)
            log.debug("stream: userId={} conversationId={} modelTier={}", userId, conversationId, modelTier)

            val userMessage = chatMessageRepository.save(
                ChatMessage(
                    conversation = conversation,
                    role = MessageRole.USER,
                    content = content,
                    status = MessageStatus.COMPLETED
                )
            )
            conversation.updatedAt = Instant.now()
            conversationRepository.save(conversation)

            val history = chatMessageRepository.findAllByConversationIdOrderByCreatedAtAsc(conversationId)
            val providerMessages = history
                .filter { it.status == MessageStatus.COMPLETED && it.id != userMessage.id }
                .map { it.toProviderMessage() } + userMessage.toProviderMessage()

            val assistantMessage = chatMessageRepository.save(
                ChatMessage(
                    conversation = conversation,
                    role = MessageRole.ASSISTANT,
                    content = "",
                    status = MessageStatus.STREAMING,
                    model = modelTier.name.lowercase(),
                )
            )

            require(assistantMessage.id > 0) { "Assistant message id must be assigned" }

            StreamContext(
                assistantMessageId = assistantMessage.id,
                providerMessages = providerMessages,
                modelTier = modelTier,
            )
        } ?: error("Failed to initialize chat stream")

        val buffer = StringBuilder()

        return llmProvider.stream(streamContext.providerMessages)
            .doOnNext { chunk -> buffer.append(chunk) }
            .doFinally { signalType -> finalizeAssistantMessage(signalType, userId, streamContext.assistantMessageId, buffer) }
    }

    /**
     * Returns persisted messages for an owned conversation.
     */
    fun getHistory(userId: Long, conversationUuid: UUID): ChatHistory {
        return transactionTemplate.execute {
            val conversation = conversationRepository.findByUuid(conversationUuid)
                ?: throw ConversationNotFoundException(conversationUuid)

            if (conversation.user.id != userId) {
                throw ConversationAccessDeniedException(conversationUuid)
            }

            ChatHistory(
                conversationUuid = conversation.uuid,
                messages = chatMessageRepository.findAllByConversationIdOrderByCreatedAtAsc(conversation.id),
            )
        } ?: error("Failed to load chat history")
    }

    /**
     * 스트림 종료 시 assistant 메시지를 확정하고 채팅 보상을 정산한다(개정 모델 CC-283 R1).
     *
     * 상태 가드: 재진입(이미 확정된 메시지)에서는 정산/환불을 건너뛴다 — STREAMING 일 때만 1회 처리하여
     * 예약 밥의 이중 정산·환불과 진화 경험치 이중 적립을 막는다. 메시지 확정과 보상 정산은 한 트랜잭션이다.
     *  - COMPLETED → chatRewardService.settle (예약 밥 소진 + cashablePt + 진화 경험치)
     *  - FAILED    → chatRewardService.refund (예약 밥 환불)
     */
    private fun finalizeAssistantMessage(
        signalType: SignalType,
        userId: Long,
        assistantMessageId: Long,
        buffer: StringBuilder,
    ) {
        val status = when (signalType) {
            SignalType.ON_COMPLETE -> MessageStatus.COMPLETED
            SignalType.ON_ERROR -> MessageStatus.FAILED
            SignalType.CANCEL -> if (buffer.isNotEmpty()) MessageStatus.COMPLETED else MessageStatus.FAILED
            else -> return
        }

        transactionTemplate.executeWithoutResult {
            val assistantMessage = chatMessageRepository.findById(assistantMessageId)
                .orElseThrow { IllegalArgumentException("Assistant message not found") }

            // 상태 가드: STREAMING 일 때만 확정·정산한다(멱등). 이미 확정된 메시지면 아무것도 하지 않는다.
            if (assistantMessage.status != MessageStatus.STREAMING) return@executeWithoutResult

            assistantMessage.content = buffer.toString()
            assistantMessage.status = status
            chatMessageRepository.save(assistantMessage)

            when (status) {
                MessageStatus.COMPLETED -> chatRewardService.settle(userId, assistantMessageId)
                MessageStatus.FAILED -> chatRewardService.refund(userId)
                else -> Unit
            }
        }
    }

    private fun ChatMessage.toProviderMessage(): LlmMessage =
        LlmMessage(
            role = when (role) {
                MessageRole.SYSTEM -> LlmMessageRole.SYSTEM
                MessageRole.USER -> LlmMessageRole.USER
                MessageRole.ASSISTANT -> LlmMessageRole.ASSISTANT
            },
            content = content
        )

    private fun ChatMessage.toResponse(): ChatMessageResponse =
        ChatMessageResponse(
            messageId = id,
            role = role.name,
            content = content,
            status = status.name,
            createdAt = createdAt,
        )

    private fun Conversation.toResponse(): ConversationResponse =
        ConversationResponse(
            conversationId = id,
            title = displayTitle(),
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun Conversation.displayTitle(): String = title.normalizedTitle()

    private fun String?.normalizedTitle(): String =
        this?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_CONVERSATION_TITLE

    private data class StreamContext(
        val assistantMessageId: Long,
        val providerMessages: List<LlmMessage>,
        val modelTier: ModelTier,
    )

    private companion object {
        const val DEFAULT_CONVERSATION_TITLE = "새 채팅"
    }
}
