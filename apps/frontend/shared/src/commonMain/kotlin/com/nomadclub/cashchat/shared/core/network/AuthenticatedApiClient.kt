package com.nomadclub.cashchat.shared.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.plugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class RefreshBody(val refreshToken: String)

@Serializable
private data class TokenPair(
    val accessToken: String,
    // GUEST 갱신(POST /api/auth/guest) 응답은 refreshToken 이 null 이므로 nullable.
    // non-null 로 두면 게스트 응답 역직렬화가 실패해 세션 갱신이 불가능해진다.
    val refreshToken: String? = null,
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
                        // GUEST 는 refreshToken 이 없으므로 빈 문자열로 보관(재인증은 deviceToken 으로 수행).
                        val newRefresh = pair.refreshToken ?: ""
                        tokenProvider.updateTokens(pair.accessToken, newRefresh)
                        BearerTokens(pair.accessToken, newRefresh)
                    } else null
                }
                sendWithoutRequest { request ->
                    val path = request.url.encodedPath
                    !(path.contains("auth/refresh") || path.contains("auth/guest"))
                }
            }
        }
    }

    /**
     * Ktor Auth 플러그인이 메모리에 캐시한 BearerTokens 를 비운다.
     * 로그아웃 시 호출하지 않으면(이 클라이언트는 싱글톤) 다음 사용자가 로그인해도
     * 401 이 날 때까지 이전 사용자의 accessToken 이 재사용될 수 있다.
     */
    fun clearTokenCache() {
        httpClient.plugin(Auth).providers
            .filterIsInstance<BearerAuthProvider>()
            .forEach { it.clearToken() }
    }

    private suspend fun refreshAccessToken(): TokenPair? {
        return when (tokenProvider.role()) {
            "MEMBER", "ADMIN" -> {
                val ref = tokenProvider.refreshToken() ?: return null
                // runCatching 은 CancellationException 까지 삼켜 구조적 동시성을 깨뜨리므로 직접 처리한다.
                try {
                    refreshClient.post("${config.baseUrl}/api/auth/refresh") {
                        contentType(ContentType.Application.Json)
                        setBody(RefreshBody(ref))
                    }.body<TokenPair>()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            }
            "GUEST" -> {
                val device = tokenProvider.deviceToken() ?: return null
                try {
                    refreshClient.post("${config.baseUrl}/api/auth/guest") {
                        parameter("deviceToken", device)
                    }.body<TokenPair>()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            }
            else -> null
        }
    }
}
