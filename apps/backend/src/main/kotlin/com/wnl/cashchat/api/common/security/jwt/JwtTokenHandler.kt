package com.wnl.cashchat.api.common.security.jwt

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.MACVerifier
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.wnl.cashchat.api.domain.user.persistence.entity.Role
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.*

@Component
class JwtTokenHandler(
    private val jwtProperties: JwtProperties
) {

    private val log = LoggerFactory.getLogger(JwtTokenHandler::class.java)
    private val signer = MACSigner(jwtProperties.secretKey.toByteArray(Charsets.UTF_8))
    private val verifier = MACVerifier(jwtProperties.secretKey.toByteArray(Charsets.UTF_8))

    fun createAccessToken(userId: Long, role: Role): String {

        val now = Date()
        val expiry = Date(now.time + jwtProperties.accessTokenExpiry)

        val claimsSet = JWTClaimsSet.Builder()
            .subject(userId.toString())
            .claim("role", role.name)
            .issueTime(now)
            .expirationTime(expiry)
            .build()

        val signedJWT = SignedJWT(JWSHeader(JWSAlgorithm.HS256), claimsSet)

        signedJWT.sign(signer)

        return signedJWT.serialize()

    }

    fun validateToken(token: String): Boolean {
        try {
            val signedJWT = SignedJWT.parse(token)
            return signedJWT.verify(verifier) && signedJWT.jwtClaimsSet.expirationTime.after(Date())
        } catch (e: Exception) {
            log.debug("Token validation failed", e)
            return false
        }
    }

    fun getUserId(token: String): Long {
        val signedJWT = SignedJWT.parse(token)
        return signedJWT.jwtClaimsSet.subject.toLong()
    }

    fun getRole(token: String): Role {
        val signedJWT = SignedJWT.parse(token)
        return Role.valueOf(signedJWT.jwtClaimsSet.getStringClaim("role"))
    }
}
