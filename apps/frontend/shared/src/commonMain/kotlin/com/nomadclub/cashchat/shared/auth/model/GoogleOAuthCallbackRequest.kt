package com.nomadclub.cashchat.shared.auth.model

import kotlinx.serialization.Serializable

/**
 * POST /api/auth/callback/google 요청 바디.
 *
 * @param code        Google OAuth serverAuthCode
 * @param deviceToken 게스트 → 회원 전환 시 기존 deviceToken 연결용 (선택)
 */
@Serializable
data class GoogleOAuthCallbackRequest(
    val code: String,
    val deviceToken: String? = null
)
