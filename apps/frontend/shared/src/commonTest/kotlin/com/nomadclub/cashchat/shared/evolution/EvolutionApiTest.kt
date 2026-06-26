package com.nomadclub.cashchat.shared.evolution

import com.nomadclub.cashchat.shared.core.network.TokenProvider
import com.nomadclub.cashchat.shared.core.network.createCashChatHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull

private object NoAuth : TokenProvider {
    override suspend fun accessToken(): String? = null
    override suspend fun refresh(): Boolean = false
}

private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

class EvolutionApiTest {

    @Test
    fun `timing session response is decoded`() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/evolution/timing-sessions", request.url.encodedPath)
            respond(
                """{"sessionId":"s1","serverStartedAt":"2026-06-26T00:00:00Z","minimumHoldMs":600,"cycleDurationMs":1800}""",
                HttpStatusCode.OK,
                jsonHeaders,
            )
        }
        val api = EvolutionApi(createCashChatHttpClient("https://api.test", NoAuth, engine), "https://api.test")

        val session = api.createTimingSession()

        assertEquals("s1", session.sessionId)
        assertEquals("2026-06-26T00:00:00Z", session.serverStartedAt)
        assertEquals(600L, session.minimumHoldMs)
        assertEquals(1800L, session.cycleDurationMs)
    }

    @Test
    fun `attempt decodes server timing result`() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/evolution/attempt", request.url.encodedPath)
            val body = (request.body as TextContent).text
            assertTrue(body.contains(""""idempotencyKey":"key""""))
            assertTrue(body.contains(""""sessionId":"s1""""))
            assertTrue(body.contains(""""releasedAtMs":1432"""))
            respond(
                """{"success":true,"fromLevel":2,"resultLevel":3,"cost":1200,"timingGrade":"PERFECT","timingBonusRate":0.10,"baseSuccessRate":0.65,"finalSuccessRate":0.75}""",
                HttpStatusCode.OK,
                jsonHeaders,
            )
        }
        val api = EvolutionApi(createCashChatHttpClient("https://api.test", NoAuth, engine), "https://api.test")

        val result = api.attempt("key", TimingAttempt("s1", 1432))

        assertEquals(true, result.success)
        assertEquals(2, result.fromLevel)
        assertEquals(3, result.resultLevel)
        assertEquals(1200L, result.cost)
        assertEquals(TimingGrade.PERFECT, result.timingGrade)
        assertEquals(0.10, result.timingBonusRate)
        assertEquals(0.65, result.baseSuccessRate)
        assertEquals(0.75, result.finalSuccessRate)
    }

    @Test
    fun `legacy attempt response keeps timing fields null`() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/evolution/attempt", request.url.encodedPath)
            val body = (request.body as TextContent).text
            assertEquals("""{"idempotencyKey":"legacy-key"}""", body)
            assertFalse(body.contains(""""timing""""))
            assertFalse(body.contains(""""sessionId""""))
            assertFalse(body.contains(""""releasedAtMs""""))
            respond(
                """{"success":false,"fromLevel":3,"resultLevel":2,"cost":1500}""",
                HttpStatusCode.OK,
                jsonHeaders,
            )
        }
        val api = EvolutionApi(createCashChatHttpClient("https://api.test", NoAuth, engine), "https://api.test")

        val result = api.attempt("legacy-key")

        assertEquals(false, result.success)
        assertEquals(3, result.fromLevel)
        assertEquals(2, result.resultLevel)
        assertEquals(1500L, result.cost)
        assertNull(result.timingGrade)
        assertNull(result.timingBonusRate)
        assertNull(result.baseSuccessRate)
        assertNull(result.finalSuccessRate)
    }
}
