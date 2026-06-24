package com.wnl.cashchat.api.domain.ad.service

import com.wnl.cashchat.api.domain.ad.exception.InvalidGoogleAdSsvCallbackException
import org.springframework.stereotype.Component
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.PublicKey
import java.security.Signature
import java.util.Base64

@Component
class GoogleAdSsvSignatureVerifier(
    private val publicKeyClient: GoogleAdPublicKeyClient,
) {
    /**
     * Google 이 raw(percent-encoded) 콘텐츠에 서명하는지 URL 디코딩된 콘텐츠에 서명하는지는 자료마다 엇갈린다
     * (공식 Java 샘플=raw, 실제 AdMob '확인' 핑 캡처=decoded). 두 형태 모두 동일 콜백에서 파생되어 어느 쪽으로도
     * 서명 위조가 불가능하므로, raw → decoded 순으로 시도해 하나라도 유효하면 통과시킨다(검증 경계 유지).
     */
    fun verify(
        signedPayload: String,
        signature: String,
        keyId: Long,
    ) {
        val decodedSignature = decodeSignature(signature)
        val publicKey = publicKeyClient.getPublicKey(keyId)

        val candidates = buildList {
            add(signedPayload)
            runCatching { urlDecode(signedPayload) }.getOrNull()?.let { decoded ->
                if (decoded != signedPayload) add(decoded)
            }
        }

        try {
            if (candidates.any { verifyOne(publicKey, it, decodedSignature) }) {
                return
            }
            throw InvalidGoogleAdSsvCallbackException("Invalid Google AdMob SSV signature")
        } catch (e: InvalidGoogleAdSsvCallbackException) {
            throw e
        } catch (e: GeneralSecurityException) {
            throw InvalidGoogleAdSsvCallbackException("Failed to verify Google AdMob SSV signature", e)
        }
    }

    private fun verifyOne(publicKey: PublicKey, payload: String, signature: ByteArray): Boolean {
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey)
        verifier.update(payload.toByteArray(Charsets.UTF_8))
        return verifier.verify(signature)
    }

    // 파서와 동일 규칙: '+' 는 리터럴 보존(Google 은 공백을 %20 으로 인코딩), '%XX' 만 디코딩.
    private fun urlDecode(payload: String): String =
        URLDecoder.decode(payload.replace("+", "%2B"), StandardCharsets.UTF_8)

    private fun decodeSignature(signature: String): ByteArray =
        try {
            Base64.getUrlDecoder().decode(signature)
        } catch (e: IllegalArgumentException) {
            throw InvalidGoogleAdSsvCallbackException("Invalid Google AdMob SSV signature encoding", e)
        }
}
