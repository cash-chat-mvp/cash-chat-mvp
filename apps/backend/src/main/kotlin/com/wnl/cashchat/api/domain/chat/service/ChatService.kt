package com.wnl.cashchat.api.domain.chat.service

import com.wnl.cashchat.api.domain.chat.persistence.entity.ChatMessage
import com.wnl.cashchat.api.domain.chat.persistence.entity.MessageRole
import com.wnl.cashchat.api.domain.chat.persistence.entity.MessageStatus
import com.wnl.cashchat.api.domain.chat.persistence.repository.ChatMessageRepository
import com.wnl.cashchat.api.domain.chat.persistence.repository.ConversationRepository
import com.wnl.cashchat.api.domain.chat.service.llm.LlmMessage
import com.wnl.cashchat.api.domain.chat.service.llm.LlmMessageRole
import com.wnl.cashchat.api.domain.chat.service.llm.LlmProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux

@Service
class ChatService(
    private val conversationRepository: ConversationRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val llmProvider: LlmProvider,
) {

    @Transactional
    fun stream(userId: Long, conversationId: Long, content: String): Flux<String> {
        val conversation = conversationRepository.findById(conversationId)
            .orElseThrow { IllegalArgumentException("Conversation not found") }

        require(conversation.user.id == userId) { "Conversation does not belong to user" }

        val userMessage = chatMessageRepository.save(
            ChatMessage(
                conversation = conversation,
                role = MessageRole.USER,
                content = content,
                status = MessageStatus.COMPLETED
            )
        )

        val history = chatMessageRepository.findAllByConversationIdOrderByCreatedAtAsc(conversationId)
            .ifEmpty { listOf(userMessage) }

        val providerMessages = history.map {
            LlmMessage(
                role = when (it.role) {
                    MessageRole.SYSTEM -> LlmMessageRole.SYSTEM
                    MessageRole.USER -> LlmMessageRole.USER
                    MessageRole.ASSISTANT -> LlmMessageRole.ASSISTANT
                },
                content = it.content
            )
        }

        val assistantMessage = chatMessageRepository.save(
            ChatMessage(
                conversation = conversation,
                role = MessageRole.ASSISTANT,
                content = "",
                status = MessageStatus.STREAMING
            )
        )

        val buffer = StringBuilder()

        return llmProvider.stream(providerMessages)
            .doOnNext { chunk -> buffer.append(chunk) }
            .doOnComplete {
                assistantMessage.content = buffer.toString()
                assistantMessage.status = MessageStatus.COMPLETED
                chatMessageRepository.save(assistantMessage)
            }
            .doOnError {
                assistantMessage.content = buffer.toString()
                assistantMessage.status = MessageStatus.FAILED
                chatMessageRepository.save(assistantMessage)
            }
    }
}
