package com.nomadclub.cashchat.shared.auth.model

import kotlinx.serialization.Serializable

/**
 * POST /api/auth/guest, POST /api/auth/refresh,
 * GET /api/auth/callback/google 공통 응답 모델.
 *
 * @param userId   서버 내부 사용자 ID
 * @param role     "GUEST" | "MEMBER" | "ADMIN"
 * @param accessToken  JWT (짧은 유효기간, 모든 API 호출에 사용)
 * @param refreshToken Rotation 방식 (MEMBER만 발급, GUEST는 null)
 */
@Serializable
data class AuthResponse(
    val userId: Long,
    val role: String,
    val accessToken: String,
    val refreshToken: String? = null
)
