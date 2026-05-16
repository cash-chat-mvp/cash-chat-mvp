package com.wnl.cashchat.api.domain.auth.oauth.apple

import com.fasterxml.jackson.annotation.JsonProperty
import com.wnl.cashchat.api.domain.auth.exception.OAuthException
import com.wnl.cashchat.api.domain.auth.oauth.properties.OAuthProperties
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException

@Component
class AppleTokenClient(
    private val oAuthProperties: OAuthProperties,
    private val restClient: RestClient,
    private val clientSecretGenerator: AppleClientSecretGenerator
) {

    fun exchangeAuthorizationCode(authorizationCode: String): AppleTokenResponse {
        val apple = oAuthProperties.apple
        val clientId = apple.clientId?.takeIf { it.isNotBlank() }
            ?: throw OAuthException("Missing Apple OAuth client id configuration")

        val formData = LinkedMultiValueMap<String, String>().apply {
            add("client_id", clientId)
            add("client_secret", clientSecretGenerator.generate())
            add("code", authorizationCode)
            add("grant_type", "authorization_code")
            apple.redirectUri?.takeIf { it.isNotBlank() }?.let { add("redirect_uri", it) }
        }

        return try {
            val response = restClient.post()
                .uri(apple.tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formData)
                .retrieve()
                .body(AppleTokenResponse::class.java)
                ?: throw OAuthException("Empty or malformed token response from Apple")

            if (response.idToken.isNullOrBlank()) {
                throw OAuthException("Apple token response does not contain 'id_token'")
            }

            response
        } catch (e: OAuthException) {
            throw e
        } catch (e: RestClientResponseException) {
            throw OAuthException("Apple token exchange failed: ${e.statusCode}", e)
        } catch (e: RestClientException) {
            throw OAuthException("Apple token exchange failed: network error", e)
        }
    }
}

data class AppleTokenResponse(
    @JsonProperty("access_token")
    val accessToken: String? = null,
    @JsonProperty("id_token")
    val idToken: String? = null,
    @JsonProperty("refresh_token")
    val refreshToken: String? = null,
    @JsonProperty("token_type")
    val tokenType: String? = null,
    @JsonProperty("expires_in")
    val expiresIn: Long? = null
)
