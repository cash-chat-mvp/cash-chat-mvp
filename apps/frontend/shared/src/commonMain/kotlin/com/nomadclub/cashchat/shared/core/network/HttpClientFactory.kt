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

    client.plugin(HttpSend).intercept { request ->
        tokenProvider.accessToken()?.let { request.bearerAuth(it) }
        var call = execute(request)
        if (call.response.status == HttpStatusCode.Unauthorized && tokenProvider.refresh()) {
            request.headers.remove(io.ktor.http.HttpHeaders.Authorization)
            tokenProvider.accessToken()?.let { request.bearerAuth(it) }
            call = execute(request)
        }
        val status = call.response.status
        if (status.value >= 400) {
            throw parseApiError(status.value, call.response.bodyAsText())
        }
        call
    }
    return client
}
