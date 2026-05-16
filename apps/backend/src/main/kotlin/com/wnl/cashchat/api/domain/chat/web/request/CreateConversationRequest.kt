package com.wnl.cashchat.api.domain.chat.web.request

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Request payload for creating a chat conversation.")
data class CreateConversationRequest @JsonCreator constructor(
    @JsonProperty("title")
    @field:Schema(description = "Initial conversation title, usually derived from the first user message.", example = "영어 공부 방법")
    val title: String? = null,
)
