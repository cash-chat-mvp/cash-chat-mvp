package com.wnl.cashchat.api.domain.auth.web.request

import jakarta.validation.constraints.NotBlank

data class AppleOAuthCallbackRequest(
    @field:NotBlank
    val authorizationCode: String,
    val identityToken: String?,
    val fullName: String?,
    val deviceToken: String?
)
