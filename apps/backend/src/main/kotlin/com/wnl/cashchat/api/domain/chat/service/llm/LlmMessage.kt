package com.wnl.cashchat.api.domain.chat.service.llm

/**
 * Represents a provider-ready chat message with a role and plain-text content.
 */
data class LlmMessage(
    val role: LlmMessageRole,
    val content: String,
)
