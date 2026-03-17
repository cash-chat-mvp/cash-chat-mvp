package com.wnl.cashchat.api.domain.auth.oauth.util

import com.wnl.cashchat.api.domain.auth.oauth.model.OAuthUserInfo
import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType

interface OAuthUserInfoExtractor {
    val providerType: AuthProviderType
    fun extract(response: Map<String, Any>): OAuthUserInfo
}