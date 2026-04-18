package com.wnl.cashchat.api.domain.chat.service.llm

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.model.StreamingChatModel
import org.springframework.ai.chat.prompt.Prompt
import reactor.core.publisher.Flux
import reactor.test.StepVerifier

@ExtendWith(MockitoExtension::class)
class GeminiLlmProviderTest {

    @Mock
    lateinit var chatModel: ChatModel

    @Mock
    lateinit var streamingChatModel: StreamingChatModel

    private lateinit var provider: GeminiLlmProvider

    @BeforeEach
    fun setUp() {
        provider = GeminiLlmProvider(chatModel, streamingChatModel)
    }

    @Test
    fun `generate returns first assistant content`() {
        whenever(chatModel.call(any<Prompt>())).thenReturn(
            ChatResponse(listOf(Generation(AssistantMessage("hi there"))))
        )

        val result = provider.generate(
            listOf(
                LlmMessage(LlmMessageRole.SYSTEM, "be kind"),
                LlmMessage(LlmMessageRole.USER, "hello")
            )
        )

        result shouldBe "hi there"
    }

    @Test
    fun `stream emits assistant chunks in order`() {
        whenever(streamingChatModel.stream(any<Prompt>())).thenReturn(
            Flux.just(
                ChatResponse(listOf(Generation(AssistantMessage("hi")))),
                ChatResponse(listOf(Generation(AssistantMessage(" there"))))
            )
        )

        StepVerifier.create(
            provider.stream(listOf(LlmMessage(LlmMessageRole.USER, "hello")))
        )
            .expectNext("hi", " there")
            .verifyComplete()
    }
}
