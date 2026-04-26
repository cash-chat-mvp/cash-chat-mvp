package com.wnl.cashchat.api.config

import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class OpenApiDocumentationTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var mockMvc: MockMvc

    init {
        test("openapi docs expose chat streaming metadata") {
            val response = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk)
                .andReturn()
                .response
                .contentAsString

            response.contains("\"title\":\"Cash Chat API\"") shouldBe true
            response.contains("\"version\":\"v1\"") shouldBe true
            response.contains("/api/v1/chat/stream") shouldBe true
            response.contains("Stream a chat completion") shouldBe true
        }
    }
}
