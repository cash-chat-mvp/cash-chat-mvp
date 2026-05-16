package com.wnl.cashchat.api.domain.chat.web.response

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "Chat conversation list item.")
data class ConversationSummaryResponse(
    @field:Schema(description = "Conversation identifier.", example = "7")
    val conversationId: Long,

    @field:Schema(description = "Conversation title.", example = "영어 공부 방법")
    val title: String,

    @field:Schema(description = "Most recent message preview.", example = "매일 짧게 공부하세요")
    val lastMessage: String?,

    @field:Schema(description = "Creation timestamp.")
    val createdAt: Instant,

    @field:Schema(description = "Last update timestamp.")
    val updatedAt: Instant,
)
