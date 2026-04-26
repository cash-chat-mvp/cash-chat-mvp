package com.wnl.cashchat.api.domain.chat.service

import com.wnl.cashchat.api.domain.chat.persistence.entity.ChatMessage
import com.wnl.cashchat.api.domain.chat.persistence.entity.MessageRole
import com.wnl.cashchat.api.domain.chat.persistence.entity.MessageStatus
import com.wnl.cashchat.api.domain.chat.persistence.repository.ChatMessageRepository
import com.wnl.cashchat.api.domain.chat.persistence.repository.ConversationRepository
import com.wnl.cashchat.api.domain.chat.service.llm.LlmMessage
import com.wnl.cashchat.api.domain.chat.service.llm.LlmMessageRole
import com.wnl.cashchat.api.domain.chat.service.llm.LlmProvider
import com.wnl.cashchat.api.domain.point.exception.InsufficientPointsException
import com.wnl.cashchat.api.domain.point.service.UserPointService
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import reactor.core.publisher.Flux
import reactor.core.publisher.SignalType

/**
 * Coordinates persistence and provider streaming for chat conversations.
 */
@Service
class ChatService(
    private val conversationRepository: ConversationRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val userPointService: UserPointService,
    private val llmProvider: LlmProvider,
    transactionManager: PlatformTransactionManager,
) {
    private val transactionTemplate = TransactionTemplate(transactionManager)

    /**
     * Streams an assistant response while persisting the user input and final assistant state.
     */
    fun stream(userId: Long, conversationId: Long, content: String): Flux<String> {
        val streamContext = transactionTemplate.execute {
            val conversation = conversationRepository.findById(conversationId)
                .orElseThrow { IllegalArgumentException("Conversation not found") }

            require(conversation.user.id == userId) { "Conversation does not belong to user" }

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

    private data class StreamContext(
        val assistantMessageId: Long,
        val providerMessages: List<LlmMessage>,
    )
}
