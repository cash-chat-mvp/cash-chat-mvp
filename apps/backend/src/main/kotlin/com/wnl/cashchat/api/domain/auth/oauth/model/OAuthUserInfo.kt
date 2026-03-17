package com.wnl.cashchat.api.domain.auth.oauth.model

data class OAuthUserInfo(
    val providerId: String,
    val email: String?,
    val name: String,
    val profileImageUrl: String?
)
