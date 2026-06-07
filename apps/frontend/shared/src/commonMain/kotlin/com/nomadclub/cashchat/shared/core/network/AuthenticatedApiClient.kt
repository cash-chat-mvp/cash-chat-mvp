package com.nomadclub.cashchat.shared.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class RefreshBody(val refreshToken: String)

@Serializable
private data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
)

/**
 * 인증된 KMM Ktor 클라이언트.
 * - 모든 요청에 Bearer accessToken 자동 주입.
 * - 401 시 refreshTokens 콜백이 토큰 갱신 후 재요청.
 *   MEMBER/ADMIN → POST /api/auth/refresh, GUEST → POST /api/auth/guest?deviceToken=...
 * - 갱신 엔드포인트는 sendWithoutRequest 로 토큰 미부착(무한 루프 방지).
 */
class AuthenticatedApiClient(
    private val config: ApiConfig,
    private val tokenProvider: TokenProvider,
    engine: HttpClientEngine,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // refresh 호출 전용(Auth 플러그인 미설치) — 갱신 중 재귀 방지
    private val refreshClient = HttpClient(engine) {
        install(ContentNegotiation) { json(json) }
    }

    val httpClient = HttpClient(engine) {
        install(ContentNegotiation) { json(json) }
        install(Auth) {
            bearer {
                loadTokens {
                    val acc = tokenProvider.accessToken()
                    val ref = tokenProvider.refreshToken()
                    if (acc != null) BearerTokens(acc, ref ?: "") else null
                }
                refreshTokens {
                    val pair = refreshAccessToken()
                    if (pair != null) {
                        tokenProvider.updateTokens(pair.accessToken, pair.refreshToken)
                        BearerTokens(pair.accessToken, pair.refreshToken)
                    } else null
                }
                sendWithoutRequest { request ->
                    val path = request.url.encodedPath
                    !(path.contains("auth/refresh") || path.contains("auth/guest"))
                }
            }
        }
    }

    private suspend fun refreshAccessToken(): TokenPair? {
        return when (tokenProvider.role()) {
            "MEMBER", "ADMIN" -> {
                val ref = tokenProvider.refreshToken() ?: return null
                runCatching {
                    refreshClient.post("${config.baseUrl}/api/auth/refresh") {
                        contentType(ContentType.Application.Json)
                        setBody(RefreshBody(ref))
                    }.body<TokenPair>()
                }.getOrNull()
            }
            "GUEST" -> {
                val device = tokenProvider.deviceToken() ?: return null
                runCatching {
                    refreshClient.post("${config.baseUrl}/api/auth/guest") {
                        parameter("deviceToken", device)
                    }.body<TokenPair>()
                }.getOrNull()
            }
            else -> null
        }
    }
}
