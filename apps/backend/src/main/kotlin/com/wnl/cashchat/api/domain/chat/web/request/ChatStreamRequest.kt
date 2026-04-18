package com.wnl.cashchat.api.domain.chat.web.request

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class ChatStreamRequest @JsonCreator constructor(
    @JsonProperty("conversationId")
    @field:NotNull
    val conversationId: Long?,

    @JsonProperty("message")
    @field:NotBlank
    val message: String,
)
