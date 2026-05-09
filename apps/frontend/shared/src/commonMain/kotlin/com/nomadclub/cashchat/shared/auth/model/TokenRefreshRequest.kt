package com.nomadclub.cashchat.shared.auth.model

import kotlinx.serialization.Serializable

/**
 * POST /api/auth/refresh 요청 바디.
 */
@Serializable
data class TokenRefreshRequest(
    val refreshToken: String
)
