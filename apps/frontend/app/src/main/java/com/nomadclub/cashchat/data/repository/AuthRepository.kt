package com.nomadclub.cashchat.data.repository

import com.nomadclub.cashchat.core.data.TokenDataStore
import com.nomadclub.cashchat.core.network.ApiService
import com.nomadclub.cashchat.shared.attendance.AttendanceStore
import com.nomadclub.cashchat.shared.core.network.AuthenticatedApiClient
import com.nomadclub.cashchat.shared.points.PointsRepository
import com.nomadclub.cashchat.shared.auth.model.AuthResponse
import com.nomadclub.cashchat.shared.auth.model.GoogleOAuthCallbackRequest
import com.nomadclub.cashchat.shared.auth.model.LogoutRequest
import com.nomadclub.cashchat.shared.auth.model.TokenRefreshRequest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class AuthRepository(
    private val apiService: ApiService,
    private val tokenDataStore: TokenDataStore,
    private val authenticatedApiClient: AuthenticatedApiClient,
    private val attendanceStore: AttendanceStore,
    private val pointsRepository: PointsRepository,
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
        val deviceToken = tokenDataStore.getOrCreateDeviceToken()
        val response = apiService.loginWithGoogle(
            GoogleOAuthCallbackRequest(code = authCode, deviceToken = deviceToken)
        )
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
            clearLocalSession()
            _reAuthRequired.tryEmit(Unit)
            error("Refresh Token 만료 (${response.code()})")
        }
        val body = response.body() ?: error("빈 응답")
        tokenDataStore.saveAuthResponse(body)
        body
    }

    /**
     * 로그아웃.
     * - OAuth 로그인 사용자(MEMBER/ADMIN): 서버에 RefreshToken 무효화 요청 후 로컬 토큰 삭제
     * - 게스트 사용자(GUEST): 서버 호출 없이 로컬 토큰만 삭제
     */
    suspend fun logout() {
        val role = tokenDataStore.getRoleBlocking()
        if (role != null && role != "GUEST") {
            val refreshToken = tokenDataStore.getRefreshTokenBlocking()
            if (refreshToken != null) {
                runCatching { apiService.logout(LogoutRequest(refreshToken)) }
            }
        }
        tokenDataStore.clearTokens()
        clearLocalSession()
    }

    /**
     * 로그아웃/세션 만료 시 메모리에 남은 사용자별 상태를 모두 비운다.
     * 싱글톤이라 비우지 않으면 다음 사용자에게 이전 사용자의 토큰/출석/잔액이 노출될 수 있다.
     * - Ktor 클라이언트가 캐시한 BearerTokens
     * - 출석(AttendanceStore) / 코인 잔액(PointsRepository) 상태
     */
    private fun clearLocalSession() {
        authenticatedApiClient.clearTokenCache()
        attendanceStore.reset()
        pointsRepository.reset()
    }

    fun getAccessToken() = tokenDataStore.getAccessTokenBlocking()
    fun getUserRole() = tokenDataStore.getRoleBlocking()
    fun getUserId() = tokenDataStore.getUserIdBlocking()
    val accessTokenFlow = tokenDataStore.accessTokenFlow
    val roleFlow = tokenDataStore.roleFlow
}
