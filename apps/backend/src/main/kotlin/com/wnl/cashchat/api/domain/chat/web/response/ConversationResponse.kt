package com.wnl.cashchat.api.domain.chat.web.response

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "Created chat conversation.")
data class ConversationResponse(
    @field:Schema(description = "Conversation identifier.", example = "7")
    val conversationId: Long,

    @field:Schema(description = "Conversation title.", example = "영어 공부 방법")
    val title: String,

    @field:Schema(description = "Creation timestamp.")
    val createdAt: Instant,

    @field:Schema(description = "Last update timestamp.")
    val updatedAt: Instant,
)
