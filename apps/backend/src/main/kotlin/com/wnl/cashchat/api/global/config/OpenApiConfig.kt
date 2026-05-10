package com.wnl.cashchat.api.global.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Registers shared OpenAPI metadata for backend Swagger documentation.
 */
@Configuration
class OpenApiConfig {

    @Bean
    fun cashChatOpenApi(): OpenAPI =
        OpenAPI().info(
            Info()
                .title("Cash Chat API")
                .description("Backend API for authentication, user data, and streamed chat responses.")
                .version("v1")
        )
}
