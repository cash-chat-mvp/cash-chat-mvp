package com.nomadclub.cashchat.shared.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
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
        // SSE 채팅 스트림 대응. 엔진 기본 read timeout(OkHttp 10s)은 백엔드 하트비트
        // 간격(15s)보다 짧아, 느린 LLM 응답 중 하트비트가 도착하기 전에 클라이언트가
        // 먼저 끊어 "응답이 끊겼어요"가 항상 뜨던 문제를 막는다.
        // - requestTimeout: 스트림 전체 길이는 제한하지 않음
        // - socketTimeout: 15s 하트비트가 리셋할 수 있도록 nginx read timeout(60s)과 맞춤
        install(HttpTimeout) {
            requestTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS
            socketTimeoutMillis = 60_000
            connectTimeoutMillis = 15_000
        }
    }
    // engine이 주어지면(테스트 MockEngine) 그대로, 아니면 플랫폼 HTTP/1.1 엔진을 사용한다.
    // SSE 스트림이 nginx HTTP/2 환경에서 응답 종료 직후 RST_STREAM(INTERNAL_ERROR)로
    // 끊기는 문제를 피하기 위해 HTTP/1.1을 강제한다([http1ClientEngine] 참고).
    val client = HttpClient(engine ?: http1ClientEngine(), config)

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

/**
 * 플랫폼별 Ktor HTTP 엔진. SSE 안정성을 위해 가능한 경우 HTTP/1.1을 강제한다.
 *
 * nginx가 `proxy_buffering off`로 SSE를 HTTP/2 클라이언트에 중계할 때, 응답이 끝나면
 * 깨끗한 END_STREAM 대신 RST_STREAM(INTERNAL_ERROR)을 보내는 사례가 있다. 그러면 OkHttp가
 * StreamResetException을 던져 정상 종료가 오류로 처리된다. HTTP/1.1은 스트림 리셋 개념이
 * 없어 연결 종료/청크 종료로 정상 마감되므로 이 문제를 회피한다.
 */
internal expect fun http1ClientEngine(): io.ktor.client.engine.HttpClientEngine
