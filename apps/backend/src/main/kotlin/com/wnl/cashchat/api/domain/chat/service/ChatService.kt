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
import com.wnl.cashchat.api.domain.chat.web.response.ChatMessageResponse
import com.wnl.cashchat.api.domain.chat.web.response.ConversationResponse
import com.wnl.cashchat.api.domain.chat.web.response.ConversationSummaryResponse
import com.wnl.cashchat.api.domain.economy.exception.FeatureDisabledException
import com.wnl.cashchat.api.domain.economy.properties.EconomyProperties
import com.wnl.cashchat.api.domain.user.persistence.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import reactor.core.publisher.Flux
import reactor.core.publisher.SignalType
import java.time.Instant
import java.util.UUID

/**
 * Coordinates persistence and provider streaming for chat conversations.
 */
@Service
class ChatService(
    private val conversationRepository: ConversationRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val userRepository: UserRepository,
    private val settlementService: ChatRewardSettlementService,
    private val economyProperties: EconomyProperties,
    private val llmProvider: LlmProvider,
    transactionManager: PlatformTransactionManager,
) {
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
     * Streams an assistant response while reserving energy at entry, settling on success,
     * and refunding on failure or cancellation.
     */
    fun stream(userId: Long, conversationId: Long, messageId: String, content: String): Flux<ChatStreamEvent> {
        return Flux.defer {
            val ctx = transactionTemplate.execute {
                if (!economyProperties.rewardChatEnabled) throw FeatureDisabledException("REWARD_CHAT_ENABLED")
                val conversation = conversationRepository.findByIdAndUserId(conversationId, userId)
                    ?: throw ConversationNotFoundException(conversationId)

                val userMessage = chatMessageRepository.save(
                    ChatMessage(
                        conversation = conversation,
                        role = MessageRole.USER,
                        content = content,
                        status = MessageStatus.COMPLETED,
                    )
                )
                conversation.updatedAt = Instant.now()
                conversationRepository.save(conversation)

                val history = chatMessageRepository.findAllByConversationIdOrderByCreatedAtAsc(conversationId)
                val providerMessages = history
                    .filter { it.status == MessageStatus.COMPLETED && it.id != userMessage.id }
                    .map { it.toProviderMessage() } + userMessage.toProviderMessage()

                val assistant = chatMessageRepository.save(
                    ChatMessage(
                        conversation = conversation,
                        role = MessageRole.ASSISTANT,
                        content = "",
                        status = MessageStatus.STREAMING,
                    )
                )
                require(assistant.id > 0) { "Assistant message id must be assigned" }

                val settlementId = settlementService.beginReservation(userId, conversationId, messageId)
                StreamContext(assistant.id, settlementId, providerMessages)
            } ?: error("Failed to initialize chat stream")

            val buffer = StringBuilder()

            Flux.concat(
                Flux.just(ChatStreamEvent.Meta(messageId, 1L) as ChatStreamEvent),
                llmProvider.stream(ctx.providerMessages)
                    .doOnNext { buffer.append(it) }
                    .map { ChatStreamEvent.Delta(it) as ChatStreamEvent },
                Flux.defer {
                    Flux.just(ChatStreamEvent.RewardSettled(persistAndSettle(userId, ctx, buffer.toString())) as ChatStreamEvent)
                },
                Flux.just(ChatStreamEvent.Done("STOP") as ChatStreamEvent),
            ).onErrorResume { e ->
                failAndRefund(userId, ctx, buffer.toString())
                Flux.error(e)
            }.doFinally { signal ->
                if (signal == SignalType.CANCEL) failAndRefund(userId, ctx, buffer.toString())
            }
        }
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

    private fun persistAndSettle(userId: Long, ctx: StreamContext, text: String): SettlementResult =
        transactionTemplate.execute {
            val assistant = chatMessageRepository.findById(ctx.assistantMessageId)
                .orElseThrow { IllegalArgumentException("Assistant message not found") }
            assistant.content = text
            assistant.status = MessageStatus.COMPLETED
            chatMessageRepository.save(assistant)
            settlementService.settle(userId, ctx.settlementId, ctx.assistantMessageId)
        } ?: error("settlement failed")

    private fun failAndRefund(userId: Long, ctx: StreamContext, text: String) {
        transactionTemplate.executeWithoutResult {
            val assistant = chatMessageRepository.findById(ctx.assistantMessageId).orElse(null)
            if (assistant != null && assistant.status == MessageStatus.STREAMING) {
                assistant.content = text
                assistant.status = MessageStatus.FAILED
                chatMessageRepository.save(assistant)
            }
            settlementService.refund(userId, ctx.settlementId, ctx.assistantMessageId)
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
        val settlementId: Long,
        val providerMessages: List<LlmMessage>,
    )

    private companion object {
        const val DEFAULT_CONVERSATION_TITLE = "새 채팅"
    }
}
