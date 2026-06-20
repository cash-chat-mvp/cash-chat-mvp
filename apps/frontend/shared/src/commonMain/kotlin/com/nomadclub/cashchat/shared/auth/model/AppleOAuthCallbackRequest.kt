package com.nomadclub.cashchat.shared.auth.model

import kotlinx.serialization.Serializable

/**
 * POST /api/auth/callback/apple 요청 바디.
 *
 * @param authorizationCode Apple ASAuthorization에서 받은 authorization code (필수)
 * @param identityToken     Apple id_token (선택 — 현재 BE 검증 미사용, 계약 필드)
 * @param fullName          사용자 이름 (Apple 최초 인증 시에만 전달, 이후 null)
 * @param deviceToken       게스트 → 회원 승격 시 기존 deviceToken 연결용 (선택)
 */
@Serializable
data class AppleOAuthCallbackRequest(
    val authorizationCode: String,
    val identityToken: String? = null,
    val fullName: String? = null,
    val deviceToken: String? = null
)
