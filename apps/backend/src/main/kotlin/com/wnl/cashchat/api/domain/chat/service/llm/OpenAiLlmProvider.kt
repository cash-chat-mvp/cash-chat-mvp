package com.wnl.cashchat.api.domain.chat.service.llm

import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.StreamingChatModel
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux

/**
 * Adapts Spring AI OpenAI chat models to the application's LLM provider interface.
 */
@Component
@Profile("prod")
class OpenAiLlmProvider(
    private val chatModel: ChatModel,
    private val streamingChatModel: StreamingChatModel,
) : LlmProvider {

    override fun generate(messages: List<LlmMessage>): String {
        val response = chatModel.call(Prompt(messages.map { it.toSpringAiMessage() }))
        return response.results.firstOrNull()?.output?.text.orEmpty()
    }

    override fun stream(messages: List<LlmMessage>, modelOverride: String?): Flux<String> {
        val springMessages = messages.map { it.toSpringAiMessage() }
        val prompt = if (modelOverride.isNullOrBlank()) {
            Prompt(springMessages)
        } else {
            Prompt(springMessages, OpenAiChatOptions.builder().model(modelOverride).build())
        }
        return streamingChatModel.stream(prompt)
            .map { it.results.firstOrNull()?.output?.text.orEmpty() }
            .filter { it.isNotEmpty() }
    }
}
