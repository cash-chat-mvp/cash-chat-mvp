package com.wnl.cashchat.api.domain.chat.service.llm

import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.StreamingChatModel
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux

/**
 * Adapts Spring AI chat models to the application's LLM provider interface.
 */
@Component
class GeminiLlmProvider(
    private val chatModel: ChatModel,
    private val streamingChatModel: StreamingChatModel,
) : LlmProvider {

    /**
     * Generates a non-streaming assistant response from the supplied message list.
     */
    override fun generate(messages: List<LlmMessage>): String {
        val response = chatModel.call(Prompt(messages.map(::toSpringMessage)))
        return response.results.firstOrNull()?.output?.text.orEmpty()
    }

    /**
     * Streams assistant chunks while preserving whitespace-only tokens.
     */
    override fun stream(messages: List<LlmMessage>): Flux<String> {
        return streamingChatModel.stream(Prompt(messages.map(::toSpringMessage)))
            .map { it.results.firstOrNull()?.output?.text.orEmpty() }
            .filter { it.isNotEmpty() }
    }

    private fun toSpringMessage(message: LlmMessage): Message =
        when (message.role) {
            LlmMessageRole.SYSTEM -> SystemMessage(message.content)
            LlmMessageRole.USER -> UserMessage(message.content)
            LlmMessageRole.ASSISTANT -> AssistantMessage(message.content)
        }
}
