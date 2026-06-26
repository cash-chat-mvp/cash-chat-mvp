package com.wnl.cashchat.api.domain.ad.service

import com.wnl.cashchat.api.domain.ad.exception.GoogleAdSsvTransientException
import com.wnl.cashchat.api.domain.ad.exception.InvalidGoogleAdSsvCallbackException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import org.mockito.kotlin.never
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
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

    test("invalid base64 signature is rejected before public key lookup") {
        val publicKeyClient = mock<GoogleAdPublicKeyClient>()
        whenever(publicKeyClient.getPublicKey(1L))
            .thenThrow(GoogleAdSsvTransientException("key server unavailable"))
        val verifier = GoogleAdSsvSignatureVerifier(publicKeyClient)

        shouldThrow<InvalidGoogleAdSsvCallbackException> {
            verifier.verify("payload=one", "not base64!", 1L)
        }
        verify(publicKeyClient, never()).getPublicKey(1L)
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

    test("signature over the url-decoded payload passes even when raw payload is percent-encoded") {
        val keyPair = ecKeyPair()
        val publicKeyClient = mock<GoogleAdPublicKeyClient>()
        whenever(publicKeyClient.getPublicKey(7L)).thenReturn(keyPair.public)
        val verifier = GoogleAdSsvSignatureVerifier(publicKeyClient)
        // 검증기에 전달되는 raw 페이로드(인코딩됨)와, Google 이 서명한 디코딩 페이로드
        val rawPayload = "ad_unit=au&reward_item=%EC%97%90&user_id=1"
        val decodedPayload = "ad_unit=au&reward_item=에&user_id=1"
        val signature = webSafeBase64Signature(decodedPayload, keyPair)

        // raw 로는 서명이 안 맞지만 decoded 로 재시도해 통과해야 한다.
        verifier.verify(rawPayload, signature, 7L)
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
