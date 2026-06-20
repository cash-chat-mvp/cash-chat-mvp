package com.wnl.cashchat.api.domain.auth.oauth.properties

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "oauth")
data class OAuthProperties(
    @field:Valid
    val registration: Map<String, RegistrationProperties> = emptyMap(),
    @field:Valid
    val provider: Map<String, ProviderProperties> = emptyMap(),
    val apple: AppleProperties = AppleProperties()
) {

    data class RegistrationProperties(
        @field:NotBlank val provider: String,
        @field:NotBlank val clientId: String,
        @field:NotBlank val clientSecret: String,
        @field:NotBlank val redirectUri: String
    ) {
        override fun toString(): String =
            "RegistrationProperties(provider=$provider, clientId=$clientId, clientSecret=****, redirectUri=$redirectUri)"
    }

    data class ProviderProperties(
        @field:NotBlank val tokenUri: String,
        @field:NotBlank val userInfoUri: String
    )

    data class AppleProperties(
        val clientId: String? = null,
        val teamId: String? = null,
        val keyId: String? = null,
        val privateKey: String? = null,
        val tokenUri: String = "https://appleid.apple.com/auth/token",
        val jwksUri: String = "https://appleid.apple.com/auth/keys",
        val redirectUri: String? = null
    ) {
        override fun toString(): String =
            "AppleProperties(clientId=$clientId, teamId=$teamId, keyId=$keyId, privateKey=****, tokenUri=$tokenUri, jwksUri=$jwksUri, redirectUri=$redirectUri)"
    }

    fun getRegistration(name: String): RegistrationProperties =
        registration[name]
            ?: throw kotlin.IllegalStateException("OAuth registration '$name' is not configured")

    fun getProvider(name: String): ProviderProperties =
        provider[name]
            ?: throw kotlin.IllegalStateException("OAuth provider '$name' is not configured")
}
