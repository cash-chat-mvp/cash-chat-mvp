package com.nomadclub.cashchat.data.model

data class AuthResponse(
    val userId: Long,
    val role: String,
    val accessToken: String,
    val refreshToken: String?
)
