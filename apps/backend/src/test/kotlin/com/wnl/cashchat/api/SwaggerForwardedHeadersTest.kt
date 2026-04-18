package com.wnl.cashchat.api

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class SwaggerForwardedHeadersTest @Autowired constructor(
    private val mockMvc: MockMvc,
) {

    @Test
    fun `openapi docs prefer forwarded https scheme from reverse proxy`() {
        mockMvc.perform(
            get("/v3/api-docs")
                .header("Host", "cashchat.duckdns.org")
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", "cashchat.duckdns.org")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.servers[0].url").value("https://cashchat.duckdns.org"))
    }
}
