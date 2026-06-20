package com.nomadclub.cashchat.shared.core.network

import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@Serializable
private data class Pong(val ok: Boolean)

private class FakeTokenProvider(var token: String? = "abc") : TokenProvider {
    var refreshCalled = 0
    override suspend fun accessToken(): String? = token
    override suspend fun refresh(): Boolean { refreshCalled++; token = "refreshed"; return true }
}

class HttpClientFactoryTest {

    @Test
    fun `Authorization 헤더에 Bearer 토큰을 붙인다`() = runTest {
        var seenAuth: String? = null
        val engine = MockEngine { request ->
            seenAuth = request.headers[HttpHeaders.Authorization]
            respond("""{"ok":true}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client = createCashChatHttpClient("https://api.test", FakeTokenProvider(), engine)
        val pong: Pong = client.get("https://api.test/ping").body()
        assertEquals(true, pong.ok)
        assertEquals("Bearer abc", seenAuth)
    }

    @Test
    fun `에러 상태코드는 ApiException으로 변환된다`() = runTest {
        val engine = MockEngine {
            respond(
                """{"code":"INSUFFICIENT_ENERGY","message":"에너지 부족"}""",
                HttpStatusCode.Conflict,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = createCashChatHttpClient("https://api.test", FakeTokenProvider(), engine)
        val exception = assertFailsWith<ApiException> { client.get("https://api.test/energy").body<Pong>() }
        assertEquals("INSUFFICIENT_ENERGY", exception.code)
        assertEquals(409, exception.httpStatus)
    }

    @Test
    fun `401이면 refresh 후 1회 재시도한다`() = runTest {
        var calls = 0
        val engine = MockEngine { request ->
            calls++
            if (request.headers[HttpHeaders.Authorization] == "Bearer refreshed") {
                respond("""{"ok":true}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                respond("""{"code":"UNAUTHORIZED","message":"x"}""", HttpStatusCode.Unauthorized, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val provider = FakeTokenProvider()
        val client = createCashChatHttpClient("https://api.test", provider, engine)
        val pong: Pong = client.get("https://api.test/me").body()
        assertEquals(true, pong.ok)
        assertEquals(1, provider.refreshCalled)
        assertEquals(2, calls)
    }

    /**
     * refresh 도중 suspend 하는 동안 여러 요청이 동시에 401을 받아도 refresh는 단 1회만
     * 실행되어야 한다(refresh 토큰 회전 서버에서 중복 refresh → 세션 무효화 race 방지).
     */
    @Test
    fun `동시 401에도 refresh는 한 번만 실행된다`() = runTest {
        // refresh 진입 시 yield 로 다른 요청들이 401 구간에 몰리도록 만든다
        val provider = object : TokenProvider {
            var token: String? = "abc"
            var refreshCalled = 0
            override suspend fun accessToken(): String? = token
            override suspend fun refresh(): Boolean {
                refreshCalled++
                repeat(5) { yield() }
                token = "refreshed"
                return true
            }
        }
        val engine = MockEngine { request ->
            if (request.headers[HttpHeaders.Authorization] == "Bearer refreshed") {
                respond("""{"ok":true}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                respond("""{"code":"UNAUTHORIZED","message":"x"}""", HttpStatusCode.Unauthorized, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val client = createCashChatHttpClient("https://api.test", provider, engine)

        val results = (1..8).map {
            async { client.get("https://api.test/me").body<Pong>().ok }
        }.awaitAll()

        assertTrue(results.all { it }, "모든 동시 요청이 성공해야 한다")
        assertEquals(1, provider.refreshCalled, "동시 401에도 refresh는 1회만 실행되어야 한다")
    }
}
