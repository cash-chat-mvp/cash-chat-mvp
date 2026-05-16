package com.wnl.cashchat.api.domain.chat.web.response

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "Persisted chat message.")
data class ChatMessageResponse(
    @field:Schema(description = "Message identifier.", example = "10")
    val messageId: Long,

    @field:Schema(description = "Message role.", example = "USER")
    val role: String,

    @field:Schema(description = "Message content.", example = "영어 공부 방법")
    val content: String,

    @field:Schema(description = "Persistence or streaming status.", example = "COMPLETED")
    val status: String,

    @field:Schema(description = "Creation timestamp.")
    val createdAt: Instant,
)
