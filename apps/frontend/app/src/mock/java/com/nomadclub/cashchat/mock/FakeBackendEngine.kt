package com.nomadclub.cashchat.mock

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel

/**
 * 경로별 canned 응답을 돌려주는 인앱 Fake 백엔드.
 * 모든 *Api 가 이 엔진을 통해 호출되므로 직렬화/에러매핑/SSE 파서가 실제로 관통된다.
 */
fun fakeBackendEngine(state: MockBackendState): HttpClientEngine = MockEngine { request ->
    val path = request.url.encodedPath
    val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    fun json(body: String) = respond(body, HttpStatusCode.OK, jsonHeaders)

    when {
        // ── 채팅 ──
        request.method == HttpMethod.Post && path == "/api/v1/chat/conversations" ->
            json("""{"conversationId":1,"title":"mock","createdAt":"2026-07-07T00:00:00Z","updatedAt":"2026-07-07T00:00:00Z"}""")

        request.method == HttpMethod.Get && path == "/api/v1/chat/conversations" -> json("[]")

        request.method == HttpMethod.Post && path == "/api/v1/chat/stream" -> {
            val sse = if (state.scenario == "chat_error") {
                "event: error\ndata: 응답 생성 중 오류가 발생했어요\n\n"
            } else {
                "data: 목킹응답: 반갑습니다 👋\n\nevent: done\ndata: [DONE]\n\n"
            }
            respond(
                ByteReadChannel(sse),
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, ContentType.Text.EventStream.toString()),
            )
        }

        // ── 잔액/에너지/진화 ──
        request.method == HttpMethod.Get && path == "/api/points/me" ->
            json("""{"balance":${state.pointsBalance}}""")
        request.method == HttpMethod.Get && path == "/api/energy/me" ->
            json("""{"energy":${state.energy},"maxEnergy":${state.maxEnergy}}""")
        request.method == HttpMethod.Get && path == "/api/evolution/me" ->
            json("""{"level":1,"isMaxLevel":false}""")

        // ── 광고 quota/nonce ──
        request.method == HttpMethod.Get && path == "/api/ads/reward/quota" ->
            json("""{"usedToday":${state.usedToday},"dailyLimit":${state.dailyLimit},"remaining":${state.remaining},"resetAtKst":"2026-07-08T00:00:00+09:00"}""")
        request.method == HttpMethod.Post && path == "/api/ads/reward/issue-nonce" ->
            json("""{"nonce":"mock-nonce","expiresAt":"2026-07-07T00:05:00Z"}""")

        // ── 오퍼월 토큰(Fake launcher 가 우회하므로 보통 미호출) ──
        request.method == HttpMethod.Post && path == "/api/offerwall/tnk/user-token" ->
            json("""{"token":"mock-tnk-token"}""")

        // ── 기본값: 빈 객체(대부분 호출부가 runCatching 로 보호) ──
        else -> json("{}")
    }
}
