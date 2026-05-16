package com.wnl.cashchat.api.domain.auth.oauth.apple

import com.wnl.cashchat.api.domain.auth.oauth.model.OAuthUserInfo
import org.springframework.stereotype.Component

@Component
class AppleUserInfoExtractor {

    fun extract(
        claims: AppleIdTokenClaims,
        fullName: String?
    ): OAuthUserInfo =
        OAuthUserInfo(
            providerId = claims.subject,
            email = claims.email,
            name = fullName?.takeIf { it.isNotBlank() } ?: "Apple User",
            profileImageUrl = null
        )
}
