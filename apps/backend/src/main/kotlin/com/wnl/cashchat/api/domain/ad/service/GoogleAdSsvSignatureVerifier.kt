package com.wnl.cashchat.api.domain.ad.service

import com.wnl.cashchat.api.domain.ad.exception.InvalidGoogleAdSsvCallbackException
import org.springframework.stereotype.Component
import java.security.GeneralSecurityException
import java.security.Signature
import java.util.Base64

@Component
class GoogleAdSsvSignatureVerifier(
    private val publicKeyClient: GoogleAdPublicKeyClient,
) {
    fun verify(
        signedPayload: String,
        signature: String,
        keyId: Long,
    ) {
        val decodedSignature = decodeSignature(signature)
        val publicKey = publicKeyClient.getPublicKey(keyId)

        try {
            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(publicKey)
            verifier.update(signedPayload.toByteArray(Charsets.UTF_8))

            if (!verifier.verify(decodedSignature)) {
                throw InvalidGoogleAdSsvCallbackException("Invalid Google AdMob SSV signature")
            }
        } catch (e: InvalidGoogleAdSsvCallbackException) {
            throw e
        } catch (e: GeneralSecurityException) {
            throw InvalidGoogleAdSsvCallbackException("Failed to verify Google AdMob SSV signature", e)
        }
    }

    private fun decodeSignature(signature: String): ByteArray =
        try {
            Base64.getUrlDecoder().decode(signature)
        } catch (e: IllegalArgumentException) {
            throw InvalidGoogleAdSsvCallbackException("Invalid Google AdMob SSV signature encoding", e)
        }
}
