package com.nomadclub.cashchat.shared.attendance

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import kotlinx.serialization.Serializable

@Serializable
data class BonusItemDto(val itemCode: String, val quantity: Int)

@Serializable
data class RewardPreviewDto(val dayCount: Int, val coin: Long, val bonusItems: List<BonusItemDto>)

@Serializable
data class CheckInDto(
    val awardedCoin: Long,
    val streakDayCount: Int,
    val bonusItems: List<BonusItemDto>,
    val nextRewardPreview: RewardPreviewDto,
)

@Serializable
data class MonthlyAttendanceDto(
    val year: Int,
    val month: Int,
    val checkedDays: List<Int>,
    val currentStreak: Int,
    val todayChecked: Boolean,
    val nextRewardPreview: RewardPreviewDto,
)

class AttendanceApi(private val client: HttpClient, private val baseUrl: String) {
    @Throws(Exception::class)
    suspend fun checkIn(): CheckInDto = client.post("$baseUrl/api/attendance/check-in").body()

    @Throws(Exception::class)
    suspend fun getMonthly(year: Int? = null, month: Int? = null): MonthlyAttendanceDto =
        client.get("$baseUrl/api/attendance/me") {
            year?.let { parameter("year", it) }
            month?.let { parameter("month", it) }
        }.body()
}
