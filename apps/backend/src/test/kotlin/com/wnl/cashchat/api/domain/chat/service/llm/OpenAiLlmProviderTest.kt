package com.wnl.cashchat.api.domain.chat.service.llm

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.model.StreamingChatModel
import org.springframework.ai.chat.prompt.Prompt
import reactor.core.publisher.Flux
import reactor.test.StepVerifier

class OpenAiLlmProviderTest : FunSpec({
    lateinit var chatModel: ChatModel
    lateinit var streamingChatModel: StreamingChatModel
    lateinit var provider: OpenAiLlmProvider

    beforeTest {
        chatModel = mock()
        streamingChatModel = mock()
        provider = OpenAiLlmProvider(chatModel, streamingChatModel)
    }

    test("generate returns the first assistant content") {
        whenever(chatModel.call(any<Prompt>())).thenReturn(
            ChatResponse(listOf(Generation(AssistantMessage("openai answer"))))
        )

        val result = provider.generate(
            listOf(
                LlmMessage(LlmMessageRole.SYSTEM, "be concise"),
                LlmMessage(LlmMessageRole.USER, "hello")
            )
        )

        result shouldBe "openai answer"
    }

    test("stream preserves whitespace chunks while dropping empty strings") {
        whenever(streamingChatModel.stream(any<Prompt>())).thenReturn(
            Flux.just(
                ChatResponse(listOf(Generation(AssistantMessage("hello")))),
                ChatResponse(listOf(Generation(AssistantMessage(" ")))),
                ChatResponse(listOf(Generation(AssistantMessage("")))),
                ChatResponse(listOf(Generation(AssistantMessage("\n")))),
                ChatResponse(listOf(Generation(AssistantMessage("world")))),
            )
        )

        StepVerifier.create(provider.stream(listOf(LlmMessage(LlmMessageRole.USER, "hello"))))
            .expectNext("hello", " ", "\n", "world")
            .verifyComplete()
    }
})
