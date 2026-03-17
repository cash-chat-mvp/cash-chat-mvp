package com.wnl.cashchat.api.domain.auth.web.response

import com.wnl.cashchat.api.domain.user.persistence.entity.Role

data class AuthResponse(
    val userId: Long,
    val role: Role,
    val accessToken: String,
    val refreshToken: String?
)