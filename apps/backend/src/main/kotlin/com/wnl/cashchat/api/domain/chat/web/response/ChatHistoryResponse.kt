package com.wnl.cashchat.api.domain.chat.web.response

import com.wnl.cashchat.api.domain.chat.persistence.entity.ChatMessage
import com.wnl.cashchat.api.domain.chat.persistence.entity.MessageRole
import com.wnl.cashchat.api.domain.chat.persistence.entity.MessageStatus
import com.wnl.cashchat.api.domain.chat.service.ChatHistory
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.UUID

@Schema(description = "Persisted chat history for a conversation.")
data class ChatHistoryResponse(
    @field:Schema(description = "Public conversation identifier.", example = "0c4fe408-6d7c-4bd9-b0f8-5fdbe2a6a6e8")
    val conversationUuid: UUID,

    @field:Schema(description = "Messages ordered by creation time.")
    val messages: List<ChatHistoryMessageResponse>,
) {
    companion object {
        fun from(history: ChatHistory): ChatHistoryResponse =
            ChatHistoryResponse(
                conversationUuid = history.conversationUuid,
                messages = history.messages.map(ChatHistoryMessageResponse::from),
            )
    }
}

@Schema(description = "Persisted chat message.")
data class ChatHistoryMessageResponse(
    @field:Schema(description = "Internal message identifier.", example = "10")
    val id: Long,

    @field:Schema(description = "Message role.", example = "USER")
    val role: MessageRole,

    @field:Schema(description = "Message content.", example = "hello")
    val content: String,

    @field:Schema(description = "Message persistence status.", example = "COMPLETED")
    val status: MessageStatus,

    @field:Schema(description = "Model used for assistant messages.", example = "gpt-4o-mini", nullable = true)
    val model: String?,

    @field:Schema(description = "Message creation time in UTC.", example = "2026-05-10T12:34:56Z")
    val createdAt: Instant,
) {
    companion object {
        fun from(message: ChatMessage): ChatHistoryMessageResponse =
            ChatHistoryMessageResponse(
                id = message.id,
                role = message.role,
                content = message.content,
                status = message.status,
                model = message.model,
                createdAt = message.createdAt,
            )
    }
}
