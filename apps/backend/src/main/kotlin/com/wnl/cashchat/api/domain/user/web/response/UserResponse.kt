package com.wnl.cashchat.api.domain.user.web.response

import com.wnl.cashchat.api.domain.user.persistence.entity.User

data class UserResponse(
    val id: Long,
    val role: String,
    val provider: String,
    val email: String?,
    val name: String,
    val profileImageUrl: String?
) {
    companion object {
        fun from(user: User) = UserResponse(
            id = user.id,
            role = user.role.name,
            provider = user.provider.name,
            email = user.email,
            name = user.name,
            profileImageUrl = user.profileImageUrl
        )
    }
}
