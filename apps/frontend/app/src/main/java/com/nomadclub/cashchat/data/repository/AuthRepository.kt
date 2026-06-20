package com.nomadclub.cashchat.data.repository

import com.nomadclub.cashchat.core.data.TokenDataStore
import com.nomadclub.cashchat.core.network.ApiService
import com.nomadclub.cashchat.core.network.TokenRefreshGate
import com.nomadclub.cashchat.shared.auth.model.AuthResponse
import com.nomadclub.cashchat.shared.auth.model.GoogleOAuthCallbackRequest
import com.nomadclub.cashchat.shared.auth.model.LogoutRequest
import com.nomadclub.cashchat.shared.auth.model.TokenRefreshRequest
import com.nomadclub.cashchat.shared.session.SessionResetter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.withLock

class AuthRepository(
    private val apiService: ApiService,
    private val tokenDataStore: TokenDataStore,
    private val refreshGate: TokenRefreshGate,
    // 지연 주입(lazy): SessionResetter → PointsRepository → HttpClient → TokenProvider → AuthRepository
    // 로 이어지는 DI 순환을 끊는다. reset()이 실제 호출될 때(로그아웃/재인증) 그래프가 이미
    // 구성된 뒤 해소되므로 StackOverflow(순환 생성)가 발생하지 않는다.
    sessionResetter: Lazy<SessionResetter>,
) {
    private val sessionResetter by sessionResetter

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
        // 공유 락으로 직렬화: TokenAuthenticator(okhttp)와 동시에 refresh 하지 않도록 보장.
        // 락 안에서 refresh 토큰을 읽으므로, 다른 경로가 먼저 회전시켰다면 항상 최신 토큰을 사용한다.
        refreshGate.mutex.withLock {
            // 게스트(GUEST)는 refresh 토큰 회전이 아니라 deviceToken 으로 재인증한다.
            // 게스트 access 토큰 만료 시 회원용 /auth/refresh 경로를 타면 refresh 토큰이
            // 없거나 무효라 항상 실패→로그아웃되므로, /auth/guest 로 토큰을 재발급한다.
            val isGuest = tokenDataStore.getRoleBlocking() == "GUEST"
            val response = if (isGuest) {
                apiService.loginAsGuest(tokenDataStore.getOrCreateDeviceToken())
            } else {
                val refreshToken = tokenDataStore.getRefreshTokenBlocking()
                if (refreshToken == null) {
                    // 회원인데 refresh token이 없으면 복구 불가 — 세션 정리 후 재인증으로 넘긴다.
                    tokenDataStore.clearTokens()
                    sessionResetter.reset()
                    _reAuthRequired.tryEmit(Unit)
                    error("Refresh Token 없음")
                }
                apiService.refreshToken(TokenRefreshRequest(refreshToken))
            }
            if (!response.isSuccessful) {
                // 인증 만료(401/403)일 때만 세션을 비운다. 일시적 5xx/네트워크 오류로
                // 멀쩡한 세션을 날려 재로그인을 강요하지 않도록 한다.
                if (response.code() == 401 || response.code() == 403) {
                    tokenDataStore.clearTokens()
                    sessionResetter.reset()
                    _reAuthRequired.tryEmit(Unit)
                }
                error("${if (isGuest) "게스트 재인증" else "Refresh"} 실패 (${response.code()})")
            }
            val body = response.body() ?: error("빈 응답")
            tokenDataStore.saveAuthResponse(body)
            body
        }
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
        // 계정 전환 시 다음 사용자에게 이전 사용자의 대화·출석·잔액 등이 노출되지 않도록 공유 스토어 초기화
        sessionResetter.reset()
    }

    fun getAccessToken() = tokenDataStore.getAccessTokenBlocking()
    fun getUserRole() = tokenDataStore.getRoleBlocking()
    fun getUserId() = tokenDataStore.getUserIdBlocking()
    val accessTokenFlow = tokenDataStore.accessTokenFlow
    val roleFlow = tokenDataStore.roleFlow
}
