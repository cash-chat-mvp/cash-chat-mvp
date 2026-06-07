package com.nomadclub.cashchat.core.data

import com.nomadclub.cashchat.shared.core.network.TokenProvider

/** TokenDataStore(DataStore) 를 shared TokenProvider 로 위임. */
class DataStoreTokenProvider(
    private val store: TokenDataStore,
) : TokenProvider {
    override fun accessToken(): String? = store.getAccessTokenBlocking()
    override fun refreshToken(): String? = store.getRefreshTokenBlocking()
    override fun role(): String? = store.getRoleBlocking()
    override fun deviceToken(): String? = store.getOrCreateDeviceTokenBlocking()
    override fun updateTokens(accessToken: String, refreshToken: String) {
        store.updateTokensBlocking(accessToken, refreshToken)
    }
}
