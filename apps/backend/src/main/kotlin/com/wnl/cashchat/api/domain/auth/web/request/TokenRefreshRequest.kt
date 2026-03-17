package com.wnl.cashchat.api.domain.auth.web.request

import jakarta.validation.constraints.NotBlank

data class TokenRefreshRequest(
    @field:NotBlank
    val refreshToken: String
)
