package com.wnl.cashchat.api.domain.ad.service

import com.fasterxml.jackson.annotation.JsonProperty
import com.wnl.cashchat.api.domain.ad.exception.GoogleAdSsvTransientException
import com.wnl.cashchat.api.domain.ad.properties.GoogleAdSsvProperties
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.time.Clock
import java.time.Instant
import java.util.Base64

@Component
class GoogleAdPublicKeyClient(
    private val restClient: RestClient,
    private val properties: GoogleAdSsvProperties,
    private val clock: Clock = Clock.systemUTC(),
) {
    @Volatile
    private var cachedKeys: CachedKeys? = null

    fun getPublicKey(keyId: Long): PublicKey {
        val keys = currentKeys()
        return keys[keyId]
            ?: throw GoogleAdSsvTransientException("Google AdMob public key not found for key_id=$keyId")
    }

    private fun currentKeys(): Map<Long, PublicKey> {
        val now = clock.instant()
        val cached = cachedKeys
        if (cached != null && now.isBefore(cached.expiresAt)) {
            return cached.keys
        }

        synchronized(this) {
            val current = cachedKeys
            if (current != null && now.isBefore(current.expiresAt)) {
                return current.keys
            }

            val fetched = fetchKeys()
            cachedKeys = CachedKeys(
                keys = fetched,
                expiresAt = now.plus(properties.publicKeyCacheTtl),
            )
            return fetched
        }
    }

    private fun fetchKeys(): Map<Long, PublicKey> {
        val response = try {
            restClient.get()
                .uri(properties.ssvPublicKeysUri)
                .retrieve()
                .body(PublicKeysResponse::class.java)
        } catch (e: RestClientException) {
            throw GoogleAdSsvTransientException("Failed to fetch Google AdMob public keys", e)
        } ?: throw GoogleAdSsvTransientException("Google AdMob public key response was empty")

        val keys = response.keys.associate { key ->
            key.keyId to decodePublicKey(key.base64)
        }

        if (keys.isEmpty()) {
            throw GoogleAdSsvTransientException("Google AdMob public key response contained no keys")
        }

        return keys
    }

    private fun decodePublicKey(base64: String): PublicKey =
        try {
            val bytes = Base64.getDecoder().decode(base64)
            KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes))
        } catch (e: IllegalArgumentException) {
            throw GoogleAdSsvTransientException("Failed to decode Google AdMob public key", e)
        } catch (e: GeneralSecurityException) {
            throw GoogleAdSsvTransientException("Failed to decode Google AdMob public key", e)
        }

    private data class CachedKeys(
        val keys: Map<Long, PublicKey>,
        val expiresAt: Instant,
    )

    private data class PublicKeysResponse(
        val keys: List<PublicKeyResponse> = emptyList(),
    )

    private data class PublicKeyResponse(
        @JsonProperty("keyId")
        val keyId: Long,
        val base64: String,
    )
}
