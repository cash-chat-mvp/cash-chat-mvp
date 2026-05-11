package com.wnl.cashchat.api.domain.auth.oauth.util.google

import com.wnl.cashchat.api.domain.auth.oauth.model.OAuthUserInfo
import com.wnl.cashchat.api.domain.auth.oauth.util.OAuthUserInfoExtractor
import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import org.springframework.stereotype.Component

@Component
class GoogleUserInfoExtractor : OAuthUserInfoExtractor {

    override val providerType = AuthProviderType.GOOGLE

    override fun extract(response: Map<String, Any>) = OAuthUserInfo(
        providerId = response["sub"] as? String
            ?: throw IllegalArgumentException("Google user info does not contain 'sub'"),
        email = response["email"] as? String,
        name = response["name"] as? String
            ?: throw IllegalArgumentException("Google user info does not contain 'name'"),
        profileImageUrl = response["picture"] as? String
    )
}