package com.nomadclub.cashchat.shared.core.network

import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
}
