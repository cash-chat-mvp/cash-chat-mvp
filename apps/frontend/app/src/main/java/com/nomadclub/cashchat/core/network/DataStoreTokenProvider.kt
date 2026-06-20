package com.nomadclub.cashchat.core.network

import com.nomadclub.cashchat.core.data.TokenDataStore
import com.nomadclub.cashchat.data.repository.AuthRepository
import com.nomadclub.cashchat.shared.core.network.TokenProvider

/**
 * 기존 TokenDataStore/AuthRepository를 shared TokenProvider로 노출.
 * refresh는 기존 Retrofit refresh 플로우를 재사용한다 — 정책 한 곳 유지.
 */
class DataStoreTokenProvider(
    private val tokenDataStore: TokenDataStore,
    private val authRepository: AuthRepository,
) : TokenProvider {
    override suspend fun accessToken(): String? = tokenDataStore.getAccessTokenBlocking()
    override suspend fun refresh(): Boolean = authRepository.refreshToken().isSuccess
}
