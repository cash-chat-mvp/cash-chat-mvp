package com.wnl.cashchat.api.domain.auth.oauth.apple

import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jwt.SignedJWT
import com.wnl.cashchat.api.domain.auth.exception.OAuthException
import com.wnl.cashchat.api.domain.auth.oauth.properties.OAuthProperties
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.Date

class AppleClientSecretGeneratorTest : FunSpec({

    val fixedClock = Clock.fixed(Instant.parse("2026-05-16T00:00:00Z"), ZoneOffset.UTC)

    test("generate creates ES256 JWT with Apple header and claims") {
        val keyPair = generateEcKeyPair()
        val generator = AppleClientSecretGenerator(
            oAuthProperties = OAuthProperties(
                apple = OAuthProperties.AppleProperties(
                    clientId = "com.nomadclub.cashchat",
                    teamId = "TEAM12345",
                    keyId = "KEY12345",
                    privateKey = keyPair.privateKeyPem,
                )
            ),
            clock = fixedClock,
        )

        val token = SignedJWT.parse(generator.generate())

        token.header.algorithm.name shouldBe "ES256"
        token.header.keyID shouldBe "KEY12345"
        token.jwtClaimsSet.issuer shouldBe "TEAM12345"
        token.jwtClaimsSet.subject shouldBe "com.nomadclub.cashchat"
        token.jwtClaimsSet.audience shouldBe listOf("https://appleid.apple.com")
        token.jwtClaimsSet.issueTime shouldBe Date.from(Instant.parse("2026-05-16T00:00:00Z"))
        token.jwtClaimsSet.expirationTime shouldBe Date.from(Instant.parse("2026-05-16T00:05:00Z"))
        token.verify(ECDSAVerifier(keyPair.publicKey)) shouldBe true
    }

    test("generate fails when private key is missing") {
        val generator = AppleClientSecretGenerator(
            oAuthProperties = OAuthProperties(
                apple = OAuthProperties.AppleProperties(
                    clientId = "com.nomadclub.cashchat",
                    teamId = "TEAM12345",
                    keyId = "KEY12345",
                    privateKey = null,
                )
            ),
            clock = fixedClock,
        )

        shouldThrow<OAuthException> {
            generator.generate()
        }.message shouldBe "Missing Apple OAuth private key configuration"
    }

    test("generate fails when private key is malformed") {
        val generator = AppleClientSecretGenerator(
            oAuthProperties = OAuthProperties(
                apple = OAuthProperties.AppleProperties(
                    clientId = "com.nomadclub.cashchat",
                    teamId = "TEAM12345",
                    keyId = "KEY12345",
                    privateKey = "not-a-private-key",
                )
            ),
            clock = fixedClock,
        )

        shouldThrow<OAuthException> {
            generator.generate()
        }.message shouldBe "Invalid Apple private key configuration"
    }
})

private data class TestKeyPair(
    val privateKeyPem: String,
    val publicKey: ECPublicKey,
)

private fun generateEcKeyPair(): TestKeyPair {
    val generator = KeyPairGenerator.getInstance("EC")
    generator.initialize(256)
    val keyPair = generator.generateKeyPair()
    val encodedPrivateKey = Base64.getMimeEncoder(64, "\n".toByteArray())
        .encodeToString(keyPair.private.encoded)

    return TestKeyPair(
        privateKeyPem = """
            -----BEGIN PRIVATE KEY-----
            $encodedPrivateKey
            -----END PRIVATE KEY-----
        """.trimIndent(),
        publicKey = keyPair.public as ECPublicKey,
    )
}
