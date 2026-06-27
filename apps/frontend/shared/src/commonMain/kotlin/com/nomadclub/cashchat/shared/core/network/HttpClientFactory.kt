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
    // engine이 주어지면(테스트 MockEngine) 그대로, 아니면 플랫폼 기본 엔진을 사용한다.
    // 과거에는 nginx HTTP/2 SSE 종료 시 RST_STREAM(INTERNAL_ERROR)을 회피하려 HTTP/1.1을
    // 강제했지만, iOS Darwin(NSURLSession)은 HTTP/1.1 강제가 불가능해 iOS SSE가 막혀 있었다.
    // 이제 백엔드(PR #189/CC-311)가 정상 종료 시 `event: done`을 명시 전송하므로, 클라이언트는
    // done 수신 후 오는 전송 리셋을 정상 종료로 흡수한다([ChatApi.streamMessage] 참고).
    // 따라서 HTTP/1.1 강제를 제거하고 양 플랫폼 모두 기본 엔진(HTTP/2 협상)을 사용한다.
    val client = HttpClient(engine ?: defaultClientEngine(), config)

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
 * 온디바이스 모델(.litertlm, ~2.5GB) 다운로드 전용 HttpClient.
 *
 * API용 [createCashChatHttpClient] 를 그대로 쓰면 안 되는 이유:
 * - 모든 요청에 CashChat JWT 를 Bearer 로 붙인다 → HuggingFace 가 외부 토큰을 보고 401 을 줄 수
 *   있고, 그러면 우리 백엔드로 refresh 까지 시도하는 엉뚱한 경로를 탄다.
 * - JSON ContentNegotiation 은 바이너리 다운로드에 불필요하다.
 * - socketTimeout 60s 는 SSE 하트비트 기준이라, NAT64/CDN 정체로 잠깐 끊겨도 바로 실패한다.
 *
 * 그래서 인증·협상 없이, 대용량 전송에 맞는 타임아웃만 둔 별도 클라이언트를 쓴다.
 * 리다이렉트(HF resolve → CDN 302)는 기본 설치된 HttpRedirect 가 따라간다.
 */
fun createModelDownloadHttpClient(
    engine: HttpClientEngine? = null,
): HttpClient = HttpClient(engine ?: defaultClientEngine()) {
    expectSuccess = false
    install(HttpTimeout) {
        requestTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS
        socketTimeoutMillis = 120_000
        connectTimeoutMillis = 30_000
    }
}

/**
 * 플랫폼별 Ktor HTTP 기본 엔진(Android: OkHttp, iOS: Darwin). HTTP 버전은 강제하지 않고
 * 서버와 협상(HTTP/2 가능)한다.
 *
 * SSE 종료 시 nginx가 RST_STREAM(INTERNAL_ERROR)/iOS -1005 로 스트림을 리셋하는 문제는
 * 백엔드(PR #189/CC-311)의 `event: done` 명시 종료 신호로 구분하고, 클라이언트가 done 이후
 * 리셋을 정상 종료로 흡수해 처리한다([ChatApi.streamMessage]).
 */
internal expect fun defaultClientEngine(): io.ktor.client.engine.HttpClientEngine
