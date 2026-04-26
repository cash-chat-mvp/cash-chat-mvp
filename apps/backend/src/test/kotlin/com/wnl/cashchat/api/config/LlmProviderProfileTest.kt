package com.wnl.cashchat.api.config

import com.wnl.cashchat.api.domain.chat.service.llm.GeminiLlmProvider
import com.wnl.cashchat.api.domain.chat.service.llm.LlmProvider
import com.wnl.cashchat.api.domain.chat.service.llm.OpenAiLlmProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(
    properties = [
        "spring.profiles.active=dev",
        "GEMINI_API_KEY=dummy-gemini-key",
        "GOOGLE_CLIENT_ID=dummy-google-client-id",
        "GOOGLE_CLIENT_SECRET=dummy-google-client-secret",
        "GOOGLE_REDIRECT_URI=http://localhost:8080/api/auth/callback/google",
    ]
)
class DevLlmProviderProfileTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var llmProvider: LlmProvider

    init {
        test("dev profile uses GeminiLlmProvider") {
            llmProvider::class shouldBe GeminiLlmProvider::class
        }
    }
}

@SpringBootTest(
    properties = [
        "spring.profiles.active=prod",
        "OPENAI_API_KEY=dummy-openai-key",
        "GOOGLE_CLIENT_ID=dummy-google-client-id",
        "GOOGLE_CLIENT_SECRET=dummy-google-client-secret",
        "GOOGLE_REDIRECT_URI=https://cashchat.duckdns.org/api/auth/callback/google",
    ]
)
class ProdLlmProviderProfileTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var llmProvider: LlmProvider

    init {
        test("prod profile uses OpenAiLlmProvider") {
            llmProvider::class shouldBe OpenAiLlmProvider::class
        }
    }
}
