package com.nomadclub.cashchat.shared.attendance

import com.nomadclub.cashchat.shared.attendance.model.CheckInResult
import com.nomadclub.cashchat.shared.attendance.model.MonthlyAttendance
import com.nomadclub.cashchat.shared.core.network.ApiConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.parameter
import kotlinx.coroutines.CancellationException

/**
 * 출석 REST 클라이언트. 인증 클라이언트(AuthenticatedApiClient.httpClient)를 주입받는다.
 * iOS 에서 호출하는 suspend 함수는 @Throws 필수.
 */
class AttendanceApiService(
    private val config: ApiConfig,
    private val httpClient: HttpClient,
) {
    @Throws(CancellationException::class, Exception::class)
    suspend fun getMonthly(year: Int? = null, month: Int? = null): MonthlyAttendance =
        httpClient.get("${config.baseUrl}/api/attendance/me") {
            if (year != null) parameter("year", year)
            if (month != null) parameter("month", month)
        }.body()

    @Throws(CancellationException::class, Exception::class)
    suspend fun checkIn(): CheckInResult =
        httpClient.post("${config.baseUrl}/api/attendance/check-in").body()
}
