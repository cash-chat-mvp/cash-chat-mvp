package com.wnl.cashchat.api.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(properties = ["spring.profiles.active=dev"])
class DevGeminiProfileTest(
    @Value("\${spring.ai.openai.base-url}") private val baseUrl: String,
    @Value("\${spring.ai.openai.chat.options.model}") private val model: String,
) {

    @Test
    fun `dev profile uses gemini openai compatible endpoint and target model`() {
        assertEquals("https://generativelanguage.googleapis.com/v1beta/openai", baseUrl)
        assertEquals("gemini-3.1-flash-lite-preview", model)
    }
}
