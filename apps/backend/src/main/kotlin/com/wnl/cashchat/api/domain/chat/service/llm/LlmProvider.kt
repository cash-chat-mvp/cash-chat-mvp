package com.wnl.cashchat.api.domain.chat.service.llm

import reactor.core.publisher.Flux

/**
 * Defines the application-facing contract for chat model providers.
 */
interface LlmProvider {
    /**
     * Generates a complete response from the supplied conversation context.
     */
    fun generate(messages: List<LlmMessage>): String

    /**
     * Streams response chunks from the supplied conversation context.
     * [modelOverride] selects a specific model (routing); null/blank uses the provider's default model.
     */
    fun stream(messages: List<LlmMessage>, modelOverride: String? = null): Flux<String>
}
