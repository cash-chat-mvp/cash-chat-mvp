package com.wnl.cashchat.api.config

import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.env.Environment

@SpringBootTest(properties = ["spring.profiles.active=dev"])
class DevGeminiProfileTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var environment: Environment

    init {
        test("dev profile uses the gemini openai compatible endpoint and target model") {
            environment.getProperty("spring.ai.openai.base-url") shouldBe
                "https://generativelanguage.googleapis.com/v1beta/openai"
            environment.getProperty("spring.ai.openai.chat.options.model") shouldBe "gemini-3.1-flash-lite-preview"
        }
    }
}
