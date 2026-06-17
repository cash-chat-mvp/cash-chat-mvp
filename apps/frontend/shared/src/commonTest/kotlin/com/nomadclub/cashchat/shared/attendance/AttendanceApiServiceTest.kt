package com.nomadclub.cashchat.shared.attendance

import com.nomadclub.cashchat.shared.core.network.ApiConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class AttendanceApiServiceTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun client(engine: MockEngine) = HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    @Test
    fun `getMonthly 는 attendance me 를 호출하고 파싱한다`() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/api/attendance/me", request.url.encodedPath)
            respond(
                """{"year":2026,"month":5,"checkedDays":[1,2,3],"currentStreak":3,"todayChecked":false,
                   "nextRewardPreview":{"dayCount":4,"coin":20,"bonusItems":[]}}""",
                HttpStatusCode.OK, jsonHeaders,
            )
        }
        val service = AttendanceApiService(ApiConfig("http://test"), client(engine))

        val result = service.getMonthly(2026, 5)

        assertEquals(3, result.currentStreak)
        assertEquals(listOf(1, 2, 3), result.checkedDays)
        assertEquals(false, result.todayChecked)
    }

    @Test
    fun `checkIn 은 POST check-in 을 호출하고 보상을 파싱한다`() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/attendance/check-in", request.url.encodedPath)
            respond(
                """{"awardedCoin":30,"streakDayCount":7,
                   "bonusItems":[{"itemCode":"EVOLVE_STONE","quantity":1}],
                   "nextRewardPreview":{"dayCount":8,"coin":20,"bonusItems":[]}}""",
                HttpStatusCode.OK, jsonHeaders,
            )
        }
        val service = AttendanceApiService(ApiConfig("http://test"), client(engine))

        val result = service.checkIn()

        assertEquals(30, result.awardedCoin)
        assertEquals(7, result.streakDayCount)
        assertEquals("EVOLVE_STONE", result.bonusItems.first().itemCode)
    }
}
