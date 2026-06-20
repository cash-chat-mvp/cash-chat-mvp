package com.nomadclub.cashchat.shared.offerwall

import com.nomadclub.cashchat.shared.core.network.ApiException
import com.nomadclub.cashchat.shared.core.network.TokenProvider
import com.nomadclub.cashchat.shared.core.network.createCashChatHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private object NoAuth : TokenProvider {
    override suspend fun accessToken(): String? = null
    override suspend fun refresh(): Boolean = false
}

private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

class OfferwallApiTest {

    @Test
    fun `user-token 을 POST 하고 token 을 파싱한다`() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/offerwall/tnk/user-token", request.url.encodedPath)
            respond("""{"token":"opaque-abc-123"}""", HttpStatusCode.OK, jsonHeaders)
        }
        val api = OfferwallApi(createCashChatHttpClient("https://api.test", NoAuth, engine), "https://api.test")
        assertEquals("opaque-abc-123", api.issueUserToken().token)
    }

    @Test
    fun `4xx 응답은 ApiException 으로 던진다`() = runTest {
        val engine = MockEngine {
            respond("""{"code":"UNAUTHORIZED","message":"x"}""", HttpStatusCode.Unauthorized, jsonHeaders)
        }
        val api = OfferwallApi(createCashChatHttpClient("https://api.test", NoAuth, engine), "https://api.test")
        assertFailsWith<ApiException> { api.issueUserToken() }
    }
}
