package com.nomadclub.cashchat.shared.attendance

import com.nomadclub.cashchat.shared.core.network.ApiConfig
import com.nomadclub.cashchat.shared.points.LocalPointsRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AttendanceStoreTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private fun http(engine: MockEngine) = HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    @Test
    fun `loadMonthly 후 상태에 출석 일자가 반영된다`() = runTest {
        val engine = MockEngine {
            respond(
                """{"year":2026,"month":6,"checkedDays":[1,2],"currentStreak":2,"todayChecked":false,
                   "nextRewardPreview":{"dayCount":3,"coin":20,"bonusItems":[]}}""",
                HttpStatusCode.OK, jsonHeaders,
            )
        }
        val service = AttendanceApiService(ApiConfig("http://test"), http(engine))
        val store = AttendanceStore(service, LocalPointsRepository(initial = 1000), scope = this)

        store.loadMonthly()

        val state = store.state.first { !it.isLoading }
        assertEquals(listOf(1, 2), state.checkedDays)
        assertEquals(2, state.currentStreak)
        assertEquals(false, state.todayChecked)
    }

    @Test
    fun `checkIn 성공 시 todayChecked true 와 코인 적립이 반영된다`() = runTest {
        var checkedIn = false
        val engine = MockEngine { request ->
            if (request.url.encodedPath.endsWith("/check-in")) {
                checkedIn = true
                respond(
                    """{"awardedCoin":30,"streakDayCount":3,"bonusItems":[],
                       "nextRewardPreview":{"dayCount":4,"coin":20,"bonusItems":[]}}""",
                    HttpStatusCode.OK, jsonHeaders,
                )
            } else {
                // 체크인 이후 월간 재조회 시점에는 서버가 실제 출석 날짜(3일)와 todayChecked=true 를 반환한다.
                val checkedDays = if (checkedIn) "[1,2,3]" else "[1,2]"
                val streak = if (checkedIn) 3 else 2
                respond(
                    """{"year":2026,"month":6,"checkedDays":$checkedDays,"currentStreak":$streak,
                       "todayChecked":$checkedIn,
                       "nextRewardPreview":{"dayCount":4,"coin":20,"bonusItems":[]}}""",
                    HttpStatusCode.OK, jsonHeaders,
                )
            }
        }
        val service = AttendanceApiService(ApiConfig("http://test"), http(engine))
        val points = LocalPointsRepository(initial = 1000)
        val store = AttendanceStore(service, points, scope = this)
        store.loadMonthly()
        store.state.first { !it.isLoading }

        store.checkIn()

        // checkIn 성공 시 todayChecked 는 즉시 true 가 되고, 이어지는 월간 재조회로 checkedDays 가 동기화된다.
        val state = store.state.first { it.todayChecked && it.checkedDays.contains(3) }
        assertTrue(checkedIn)
        assertEquals(3, state.currentStreak)
        // 로컬 추론이 아닌 서버 재조회로 동기화된 실제 출석 날짜가 반영되어야 한다.
        assertTrue(state.checkedDays.contains(3), "checkedDays should contain the server-confirmed day")
        assertEquals(listOf(1, 2, 3), state.checkedDays)
        assertEquals(1030, points.balance.first())
    }

    @Test
    fun `checkIn 성공 후 월간 재조회가 실패해도 출석 성공 상태는 유지된다`() = runTest {
        var checkedIn = false
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/check-in") -> {
                    checkedIn = true
                    respond(
                        """{"awardedCoin":30,"streakDayCount":3,"bonusItems":[],
                           "nextRewardPreview":{"dayCount":4,"coin":20,"bonusItems":[]}}""",
                        HttpStatusCode.OK, jsonHeaders,
                    )
                }
                // 체크인 직후의 월간 재조회는 일시적 서버 오류로 실패한다.
                checkedIn -> respond("", HttpStatusCode.InternalServerError, jsonHeaders)
                else -> respond(
                    """{"year":2026,"month":6,"checkedDays":[1,2],"currentStreak":2,"todayChecked":false,
                       "nextRewardPreview":{"dayCount":3,"coin":20,"bonusItems":[]}}""",
                    HttpStatusCode.OK, jsonHeaders,
                )
            }
        }
        val service = AttendanceApiService(ApiConfig("http://test"), http(engine))
        val points = LocalPointsRepository(initial = 1000)
        val store = AttendanceStore(service, points, scope = this)
        store.loadMonthly()
        store.state.first { !it.isLoading }

        val rewardEvent = async(start = CoroutineStart.UNDISPATCHED) {
            store.rewardEvents.first()
        }

        store.checkIn()

        // 재조회 실패와 무관하게 체크인 성공 상태(코인·보상·todayChecked)는 확정되어야 한다.
        val state = store.state.first { it.todayChecked }
        assertEquals(3, state.currentStreak)
        assertEquals(false, state.isCheckingIn)
        assertEquals(null, state.errorMessage)
        assertEquals(1030, points.balance.first())
        assertEquals(30, rewardEvent.await().awardedCoin)
    }
}
