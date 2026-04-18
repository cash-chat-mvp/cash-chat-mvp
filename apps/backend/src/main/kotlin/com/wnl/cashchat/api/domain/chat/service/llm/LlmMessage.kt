package com.wnl.cashchat.api.domain.chat.service.llm

data class LlmMessage(
    val role: LlmMessageRole,
    val content: String,
)
