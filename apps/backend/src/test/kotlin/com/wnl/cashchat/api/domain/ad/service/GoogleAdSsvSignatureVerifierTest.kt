package com.wnl.cashchat.api.domain.ad.service

import com.wnl.cashchat.api.domain.ad.exception.GoogleAdSsvTransientException
import com.wnl.cashchat.api.domain.ad.exception.InvalidGoogleAdSsvCallbackException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class GoogleAdSsvSignatureVerifierTest : FunSpec({
    test("valid ECDSA SHA256 DER signature passes") {
        val keyPair = ecKeyPair()
        val publicKeyClient = mock<GoogleAdPublicKeyClient>()
        whenever(publicKeyClient.getPublicKey(1916455855L)).thenReturn(keyPair.public)
        val verifier = GoogleAdSsvSignatureVerifier(publicKeyClient)
        val signedPayload = "ad_unit=ad-unit&reward_amount=10&user_id=user-42"
        val signature = webSafeBase64Signature(signedPayload, keyPair)

        verifier.verify(signedPayload, signature, 1916455855L)
    }

    test("invalid signature throws invalid callback exception") {
        val keyPair = ecKeyPair()
        val publicKeyClient = mock<GoogleAdPublicKeyClient>()
        whenever(publicKeyClient.getPublicKey(1L)).thenReturn(keyPair.public)
        val verifier = GoogleAdSsvSignatureVerifier(publicKeyClient)
        val signature = webSafeBase64Signature("payload=one", keyPair)

        shouldThrow<InvalidGoogleAdSsvCallbackException> {
            verifier.verify("payload=two", signature, 1L)
        }
    }

    test("invalid base64 signature throws invalid callback exception") {
        val keyPair = ecKeyPair()
        val publicKeyClient = mock<GoogleAdPublicKeyClient>()
        whenever(publicKeyClient.getPublicKey(1L)).thenReturn(keyPair.public)
        val verifier = GoogleAdSsvSignatureVerifier(publicKeyClient)

        shouldThrow<InvalidGoogleAdSsvCallbackException> {
            verifier.verify("payload=one", "not base64!", 1L)
        }
    }

    test("transient public key client failures propagate") {
        val publicKeyClient = mock<GoogleAdPublicKeyClient>()
        whenever(publicKeyClient.getPublicKey(1L))
            .thenThrow(GoogleAdSsvTransientException("key server unavailable"))
        val verifier = GoogleAdSsvSignatureVerifier(publicKeyClient)

        shouldThrow<GoogleAdSsvTransientException> {
            verifier.verify("payload=one", "MEU", 1L)
        }
    }
})

private fun ecKeyPair(): KeyPair {
    val generator = KeyPairGenerator.getInstance("EC")
    generator.initialize(256)
    return generator.generateKeyPair()
}

private fun webSafeBase64Signature(signedPayload: String, keyPair: KeyPair): String {
    val signature = Signature.getInstance("SHA256withECDSA")
    signature.initSign(keyPair.private)
    signature.update(signedPayload.toByteArray(Charsets.UTF_8))
    return Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign())
}
