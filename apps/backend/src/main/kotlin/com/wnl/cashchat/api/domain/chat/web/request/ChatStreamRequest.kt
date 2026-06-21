package com.wnl.cashchat.api.domain.chat.web.request

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

/**
 * Carries the conversation id, idempotency key, and user message for a streamed chat response.
 */
@Schema(description = "Request payload for starting a streamed assistant response.")
data class ChatStreamRequest @JsonCreator constructor(
    @JsonProperty("conversationId")
    @field:NotNull
    @field:Schema(description = "Identifier of the conversation to continue.", example = "7")
    val conversationId: Long?,

    @JsonProperty("messageId")
    @field:NotBlank
    @field:Schema(description = "Client-generated idempotency key for this user message.", example = "msg_01H...")
    val messageId: String,

    @JsonProperty("message")
    @field:NotBlank
    @field:Schema(description = "User message to append to the conversation.", example = "Hello there")
    val message: String,
)
