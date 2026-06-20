package com.wnl.cashchat.api.domain.auth.oauth.apple

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.wnl.cashchat.api.domain.auth.exception.OAuthException
import com.wnl.cashchat.api.domain.auth.oauth.properties.OAuthProperties
import org.springframework.stereotype.Component
import java.security.KeyFactory
import java.security.interfaces.ECPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Clock
import java.time.Duration
import java.util.Base64
import java.util.Date

@Component
class AppleClientSecretGenerator(
    private val oAuthProperties: OAuthProperties,
    private val clock: Clock = Clock.systemUTC()
) {

    companion object {
        private const val APPLE_AUDIENCE = "https://appleid.apple.com"
        private val CLIENT_SECRET_TTL: Duration = Duration.ofMinutes(5)
    }

    fun generate(): String {
        val apple = oAuthProperties.apple
        val clientId = apple.clientId.required("client id")
        val teamId = apple.teamId.required("team id")
        val keyId = apple.keyId.required("key id")
        val privateKey = parsePrivateKey(apple.privateKey.required("private key"))

        val now = clock.instant()
        val claimsSet = JWTClaimsSet.Builder()
            .issuer(teamId)
            .subject(clientId)
            .audience(APPLE_AUDIENCE)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plus(CLIENT_SECRET_TTL)))
            .build()

        val header = JWSHeader.Builder(JWSAlgorithm.ES256)
            .keyID(keyId)
            .build()

        return SignedJWT(header, claimsSet)
            .apply { sign(ECDSASigner(privateKey)) }
            .serialize()
    }

    private fun parsePrivateKey(rawPrivateKey: String): ECPrivateKey {
        val normalized = rawPrivateKey
            .replace("\\n", "\n")
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")

        return try {
            val decoded = Base64.getDecoder().decode(normalized)
            val spec = PKCS8EncodedKeySpec(decoded)
            KeyFactory.getInstance("EC").generatePrivate(spec) as ECPrivateKey
        } catch (e: Exception) {
            throw OAuthException("Invalid Apple private key configuration", e)
        }
    }

    private fun String?.required(name: String): String =
        this?.takeIf { it.isNotBlank() }
            ?: throw OAuthException("Missing Apple OAuth $name configuration")
}
