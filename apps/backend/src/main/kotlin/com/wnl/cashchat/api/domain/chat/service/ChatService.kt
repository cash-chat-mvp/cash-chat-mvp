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
import com.wnl.cashchat.api.domain.point.exception.InsufficientPointsException
import com.wnl.cashchat.api.domain.point.service.UserPointService
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
    private val userPointService: UserPointService,
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

    fun listConversations(userId: Long): List<ConversationSummaryResponse> =
        conversationRepository.findAllByUserIdOrderByUpdatedAtDesc(userId)
            .map { conversation ->
                val latestMessage = chatMessageRepository.findTopByConversationIdOrderByCreatedAtDesc(conversation.id)
                ConversationSummaryResponse(
                    conversationId = conversation.id,
                    title = conversation.displayTitle(),
                    lastMessage = latestMessage?.content,
                    createdAt = conversation.createdAt,
                    updatedAt = conversation.updatedAt,
                )
            }

    fun getMessages(userId: Long, conversationId: Long): List<ChatMessageResponse> {
        conversationRepository.findByIdAndUserId(conversationId, userId)
            ?: throw IllegalArgumentException("Conversation not found")

        return chatMessageRepository.findAllByConversationIdOrderByCreatedAtAsc(conversationId)
            .map { it.toResponse() }
    }

    /**
     * Streams an assistant response while persisting the user input and final assistant state.
     */
    fun stream(userId: Long, conversationId: Long, content: String): Flux<String> {
        val streamContext = transactionTemplate.execute {
            val conversation = conversationRepository.findByIdAndUserId(conversationId, userId)
                ?: throw IllegalArgumentException("Conversation not found")

            if (!userPointService.hasEnoughBalance(userId)) {
                throw InsufficientPointsException()
            }

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
                    status = MessageStatus.STREAMING
                )
            )

            require(assistantMessage.id > 0) { "Assistant message id must be assigned" }

            StreamContext(
                assistantMessageId = assistantMessage.id,
                providerMessages = providerMessages,
            )
        } ?: error("Failed to initialize chat stream")

        val buffer = StringBuilder()

        return llmProvider.stream(streamContext.providerMessages)
            .doOnNext { chunk -> buffer.append(chunk) }
            .doFinally { signalType -> finalizeAssistantMessage(signalType, streamContext.assistantMessageId, buffer) }
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

    private fun finalizeAssistantMessage(
        signalType: SignalType,
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
            assistantMessage.content = buffer.toString()
            assistantMessage.status = status
            chatMessageRepository.save(assistantMessage)
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
    )

    private companion object {
        const val DEFAULT_CONVERSATION_TITLE = "새 채팅"
    }
}
