package com.nomadclub.cashchat.shared.core.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeTokenProvider(
    private var access: String?,
    private var refresh: String?,
    private var roleValue: String? = "MEMBER",
) : TokenProvider {
    var updatedAccess: String? = null
    override fun accessToken() = access
    override fun refreshToken() = refresh
    override fun role() = roleValue
    override fun deviceToken() = "device-1"
    override fun updateTokens(accessToken: String, refreshToken: String) {
        access = accessToken; refresh = refreshToken; updatedAccess = accessToken
    }
}

class AuthenticatedApiClientTest {

    @Test
    fun `요청에 Bearer 액세스 토큰을 주입한다`() = runTest {
        var seenAuth: String? = null
        val engine = MockEngine { request ->
            seenAuth = request.headers[HttpHeaders.Authorization]
            respond("ok", HttpStatusCode.OK)
        }
        val provider = FakeTokenProvider(access = "acc-1", refresh = "ref-1")
        val client = AuthenticatedApiClient(ApiConfig("http://test"), provider, engine).httpClient

        val res = client.get("http://test/api/users/me").bodyAsText()

        assertEquals("ok", res)
        assertEquals("Bearer acc-1", seenAuth)
    }

    @Test
    fun `401 응답 시 refresh 후 새 토큰으로 재요청한다`() = runTest {
        val provider = FakeTokenProvider(access = "old", refresh = "ref-1")
        var calls = 0
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/api/auth/refresh") ->
                    respond(
                        """{"accessToken":"new","refreshToken":"ref-2","role":"MEMBER","userId":1}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                else -> {
                    calls++
                    if (calls == 1) respond("unauthorized", HttpStatusCode.Unauthorized)
                    else respond("protected", HttpStatusCode.OK)
                }
            }
        }
        val client = AuthenticatedApiClient(ApiConfig("http://test"), provider, engine).httpClient

        val res = client.get("http://test/api/attendance/me").bodyAsText()

        assertEquals("protected", res)
        assertEquals("new", provider.updatedAccess)
        assertTrue(calls == 2)
    }
}
