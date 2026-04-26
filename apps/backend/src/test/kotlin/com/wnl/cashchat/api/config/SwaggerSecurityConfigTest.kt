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
        "app.swagger.enabled=false",
    ]
)
@AutoConfigureMockMvc
class ProdDisabledSwaggerSecurityConfigTest @Autowired constructor(
    private val mockMvc: MockMvc,
) {

    @Test
    fun `prod blocks openapi docs when swagger flag is disabled`() {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `prod blocks swagger ui when swagger flag is disabled`() {
        mockMvc.perform(get("/swagger-ui/index.html"))
            .andExpect(status().isForbidden)
    }
}

@SpringBootTest(
    properties = [
        "spring.profiles.active=prod",
        "OPENAI_API_KEY=dummy-openai-key",
        "GOOGLE_CLIENT_ID=dummy-google-client-id",
        "GOOGLE_CLIENT_SECRET=dummy-google-client-secret",
        "GOOGLE_REDIRECT_URI=https://cashchat.duckdns.org/api/auth/callback/google",
        "app.swagger.enabled=true",
    ]
)
@AutoConfigureMockMvc
class ProdEnabledSwaggerSecurityConfigTest @Autowired constructor(
    private val mockMvc: MockMvc,
) {

    @Test
    fun `prod permits openapi docs when swagger flag is enabled`() {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
    }

    @Test
    fun `prod permits swagger ui when swagger flag is enabled`() {
        mockMvc.perform(get("/swagger-ui/index.html"))
            .andExpect(status().isOk)
    }
}
