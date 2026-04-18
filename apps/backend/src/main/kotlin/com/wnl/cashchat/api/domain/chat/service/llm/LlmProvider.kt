package com.wnl.cashchat.api.domain.chat.service.llm

import reactor.core.publisher.Flux

interface LlmProvider {
    fun generate(messages: List<LlmMessage>): String
    fun stream(messages: List<LlmMessage>): Flux<String>
}
