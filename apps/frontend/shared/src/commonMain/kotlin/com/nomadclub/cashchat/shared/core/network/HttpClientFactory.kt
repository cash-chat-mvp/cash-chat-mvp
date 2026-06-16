package com.nomadclub.cashchat.shared.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.plugin
import io.ktor.client.request.bearerAuth
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * CashChat 공통 HttpClient.
 * - Bearer 토큰 자동 첨부, 401 시 1회 refresh 후 재시도
 * - 4xx/5xx 응답을 [ApiException]으로 변환 (SSE 스트림 요청은 호출부에서 별도 처리)
 * - [engine]은 테스트(MockEngine)용. null이면 플랫폼 기본 엔진.
 */
fun createCashChatHttpClient(
    baseUrl: String,
    tokenProvider: TokenProvider,
    engine: HttpClientEngine? = null,
): HttpClient {
    val config: io.ktor.client.HttpClientConfig<*>.() -> Unit = {
        expectSuccess = false
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
        }
    }
    val client = if (engine != null) HttpClient(engine, config) else HttpClient(config)

    // 동시 401 발생 시 refresh가 중복 실행되는 것을 막는다(refresh 토큰 회전 서버 대응).
    // 여러 요청이 만료된 access 토큰으로 동시에 401을 받으면 각자 refresh를 시도하는데,
    // 서버가 refresh 토큰을 회전시키면 첫 호출만 성공하고 나머지는 401 → 방금 갱신된
    // 토큰까지 무효화/로그아웃되는 race가 생긴다. Mutex로 직렬화하고, 락 진입 시 이미
    // 다른 요청이 갱신해 둔 경우 네트워크 refresh를 건너뛴다(single-flight).
    val refreshMutex = Mutex()

    client.plugin(HttpSend).intercept { request ->
        val sentToken = tokenProvider.accessToken()
        sentToken?.let { request.bearerAuth(it) }
        var call = execute(request)
        if (call.response.status == HttpStatusCode.Unauthorized) {
            val refreshed = refreshMutex.withLock {
                // 락 대기 중 다른 요청이 이미 토큰을 갱신했다면 refresh 재호출 없이 통과
                if (tokenProvider.accessToken() != sentToken) true
                else tokenProvider.refresh()
            }
            if (refreshed) {
                request.headers.remove(io.ktor.http.HttpHeaders.Authorization)
                tokenProvider.accessToken()?.let { request.bearerAuth(it) }
                call = execute(request)
            }
        }
        val status = call.response.status
        if (status.value >= 400) {
            throw parseApiError(status.value, call.response.bodyAsText())
        }
        call
    }
    return client
}
