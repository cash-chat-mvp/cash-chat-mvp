package com.wnl.cashchat.api.common.security.jwt

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "jwt")
data class JwtProperties(
    val secretKey: String,
    val accessTokenExpiry: Long,
    val refreshTokenExpiry: Long
) {
    init {
        require(secretKey.isNotBlank()) { "jwt.secretKey must not be blank" }
        require(accessTokenExpiry > 0) { "jwt.accessTokenExpiry must be greater than 0" }
        require(refreshTokenExpiry > 0) { "jwt.refreshTokenExpiry must be greater than 0" }
    }
}
