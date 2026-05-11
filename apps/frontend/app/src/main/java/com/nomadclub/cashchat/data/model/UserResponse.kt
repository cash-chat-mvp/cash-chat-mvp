package com.nomadclub.cashchat.data.model

data class UserResponse(
    val id: Long,
    val role: String,
    val provider: String?,
    val email: String?,
    val name: String?,
    val profileImageUrl: String?
)
