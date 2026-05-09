package com.nomadclub.cashchat.data.repository

import com.nomadclub.cashchat.core.data.TokenDataStore
import com.nomadclub.cashchat.core.network.ApiService
import com.nomadclub.cashchat.shared.auth.model.AuthResponse
import com.nomadclub.cashchat.shared.auth.model.TokenRefreshRequest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class AuthRepository(
    private val apiService: ApiService,
    private val tokenDataStore: TokenDataStore
) {
    // TokenAuthenticator에서 RefreshToken도 만료된 경우 재로그인 필요를 알리는 이벤트
    private val _reAuthRequired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val reAuthRequired = _reAuthRequired.asSharedFlow()

    suspend fun loginAsGuest(): Result<AuthResponse> = runCatching {
        val deviceToken = tokenDataStore.getOrCreateDeviceToken()
        val response = apiService.loginAsGuest(deviceToken)
        val body = response.body() ?: error("빈 응답: ${response.code()}")
        tokenDataStore.saveAuthResponse(body)
        body
    }

    suspend fun loginWithGoogle(authCode: String): Result<AuthResponse> = runCatching {
        // 게스트로 사용 중이었다면 deviceToken을 함께 전송 → 게스트 → 회원 전환
        // suspend 버전 사용으로 불필요한 스레드 차단 방지
        val deviceToken = tokenDataStore.getOrCreateDeviceToken()
        val response = apiService.loginWithGoogle(code = authCode, deviceToken = deviceToken)
        val body = response.body() ?: error("빈 응답: ${response.code()}")
        tokenDataStore.saveAuthResponse(body)
        body
    }

    suspend fun refreshToken(): Result<AuthResponse> = runCatching {
        val refreshToken = tokenDataStore.getRefreshTokenBlocking()
            ?: error("Refresh Token 없음")
        val response = apiService.refreshToken(TokenRefreshRequest(refreshToken))
        if (!response.isSuccessful) {
            tokenDataStore.clearTokens()
            _reAuthRequired.tryEmit(Unit)
            error("Refresh Token 만료 (${response.code()})")
        }
        val body = response.body() ?: error("빈 응답")
        tokenDataStore.saveAuthResponse(body)
        body
    }

    suspend fun logout() {
        tokenDataStore.clearTokens()
    }

    fun getAccessToken() = tokenDataStore.getAccessTokenBlocking()
    fun getUserRole() = tokenDataStore.getRoleBlocking()
    fun getUserId() = tokenDataStore.getUserIdBlocking()
    val accessTokenFlow = tokenDataStore.accessTokenFlow
    val roleFlow = tokenDataStore.roleFlow
}
