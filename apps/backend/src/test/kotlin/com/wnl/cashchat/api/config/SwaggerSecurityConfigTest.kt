package com.wnl.cashchat.api.config

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(
    properties = [
        "spring.profiles.active=prod",
        "OPENAI_API_KEY=dummy-openai-key",
        "GOOGLE_CLIENT_ID=dummy-google-client-id",
        "GOOGLE_CLIENT_SECRET=dummy-google-client-secret",
        "GOOGLE_REDIRECT_URI=https://cashchat.duckdns.org/api/auth/callback/google",
    ]
)
@AutoConfigureMockMvc
class ProdDisabledSwaggerSecurityConfigTest @Autowired constructor(
    private val mockMvc: MockMvc,
) {

    @Test
    fun `prod blocks openapi docs`() {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `prod blocks swagger ui`() {
        mockMvc.perform(get("/swagger-ui/index.html"))
            .andExpect(status().isUnauthorized)
    }
}
