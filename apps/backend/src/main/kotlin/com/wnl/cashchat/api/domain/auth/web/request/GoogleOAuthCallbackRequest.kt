package com.wnl.cashchat.api.domain.auth.web.request

import jakarta.validation.constraints.NotBlank

data class GoogleOAuthCallbackRequest(
    @field:NotBlank
    val code: String,
    val deviceToken: String?
)
