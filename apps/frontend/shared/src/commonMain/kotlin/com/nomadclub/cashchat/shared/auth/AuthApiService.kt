package com.nomadclub.cashchat.shared.auth

import com.nomadclub.cashchat.shared.auth.model.AppleOAuthCallbackRequest
import com.nomadclub.cashchat.shared.auth.model.AuthResponse
import com.nomadclub.cashchat.shared.auth.model.GoogleOAuthCallbackRequest
import com.nomadclub.cashchat.shared.auth.model.LogoutRequest
import com.nomadclub.cashchat.shared.auth.model.TokenRefreshRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/**
 * 인증 관련 REST API 클라이언트 (KMM 공통).
 *
 * Ktor HttpClient는 플랫폼별 엔진으로 동작:
 *   Android → OkHttp (shared/androidMain)
 *   iOS     → Darwin (shared/iosMain)
 *
 * @param baseUrl API 서버 기본 URL (예: "http://cashchat.duckdns.org")
 */
class AuthApiService(private val baseUrl: String) {

    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true  // 서버가 응답에 새 필드를 추가해도 파싱 실패하지 않음
                isLenient = true
            })
        }
    }

    /**
     * 게스트 로그인.
     * DeviceToken(UUID v4)으로 호출하며, 처음이면 새 Guest 계정을 생성하고
     * 재호출 시 기존 계정을 찾아 새 accessToken을 발급합니다.
     *
     * POST /api/auth/guest?deviceToken={deviceToken}
     */
    @Throws(CancellationException::class, Exception::class)
    suspend fun loginAsGuest(deviceToken: String): AuthResponse {
        return httpClient.post("$baseUrl/api/auth/guest") {
            parameter("deviceToken", deviceToken)
        }.body()
    }

    /**
     * Google OAuth 로그인 (Member 전환).
     * Android Google Sign-In SDK에서 받은 serverAuthCode를 BE로 전달하면
     * BE가 Google과 직접 교환하여 사용자 정보를 획득합니다.
     *
     * POST /api/auth/callback/google
     */
    @Throws(CancellationException::class, Exception::class)
    suspend fun loginWithGoogle(serverAuthCode: String, deviceToken: String): AuthResponse {
        return httpClient.post("$baseUrl/api/auth/callback/google") {
            contentType(ContentType.Application.Json)
            setBody(GoogleOAuthCallbackRequest(code = serverAuthCode, deviceToken = deviceToken))
        }.body()
    }

    /**
     * Apple OAuth 로그인 (Member 전환).
     * iOS ASAuthorization에서 받은 authorizationCode를 BE로 전달하면
     * BE가 Apple token endpoint와 직접 교환하여 id_token을 검증합니다.
     *
     * POST /api/auth/callback/apple
     */
    @Throws(CancellationException::class, Exception::class)
    suspend fun loginWithApple(
        authorizationCode: String,
        identityToken: String?,
        fullName: String?,
        deviceToken: String
    ): AuthResponse {
        return httpClient.post("$baseUrl/api/auth/callback/apple") {
            contentType(ContentType.Application.Json)
            setBody(
                AppleOAuthCallbackRequest(
                    authorizationCode = authorizationCode,
                    identityToken = identityToken,
                    fullName = fullName,
                    deviceToken = deviceToken
                )
            )
        }.body()
    }

    /**
     * 로그아웃 — 서버 측 RefreshToken 무효화.
     * GUEST 사용자는 호출하지 않습니다.
     *
     * POST /api/auth/logout
     */
    @Throws(CancellationException::class, Exception::class)
    suspend fun logout(refreshToken: String) {
        httpClient.post("$baseUrl/api/auth/logout") {
            contentType(ContentType.Application.Json)
            setBody(LogoutRequest(refreshToken))
        }
    }

    /**
     * Refresh Token으로 새 AccessToken + RefreshToken 발급 (Rotation).
     * 기존 RefreshToken은 서버에서 즉시 삭제됩니다.
     *
     * POST /api/auth/refresh
     */
    @Throws(CancellationException::class, Exception::class)
    suspend fun refreshToken(refreshToken: String): AuthResponse {
        return httpClient.post("$baseUrl/api/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(TokenRefreshRequest(refreshToken))
        }.body()
    }
}
