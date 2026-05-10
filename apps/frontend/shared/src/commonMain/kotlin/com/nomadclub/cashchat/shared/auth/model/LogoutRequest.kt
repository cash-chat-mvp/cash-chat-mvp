package com.nomadclub.cashchat.shared.auth.model

import kotlinx.serialization.Serializable

/**
 * POST /api/auth/logout 요청 바디.
 *
 * @param refreshToken 서버 측 RefreshToken 무효화용
 */
@Serializable
data class LogoutRequest(
    val refreshToken: String
)
