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
 *
 * ⚠️ KMM 통합 전용 모델 (shared 모듈).
 * Android 전용 구현은 app/data/model/AuthResponse.kt 참고.
 * :shared 모듈이 빌드에 포함되면 두 모델을 이 버전으로 통합 예정.
 */
@Serializable
data class AuthResponse(
    val userId: Long,
    val role: String,
    val accessToken: String,
    val refreshToken: String? = null
)
