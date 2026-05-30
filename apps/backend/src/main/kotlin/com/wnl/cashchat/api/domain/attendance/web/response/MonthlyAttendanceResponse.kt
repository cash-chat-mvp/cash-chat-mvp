package com.wnl.cashchat.api.domain.attendance.web.response

import com.wnl.cashchat.api.domain.attendance.service.MonthlyAttendance

data class MonthlyAttendanceResponse(
    val year: Int,
    val month: Int,
    val checkedDays: List<Int>,
    val currentStreak: Int,
    val todayChecked: Boolean,
    val nextRewardPreview: RewardPreviewResponse,
) {
    companion object {
        fun from(result: MonthlyAttendance): MonthlyAttendanceResponse =
            MonthlyAttendanceResponse(
                year = result.year,
                month = result.month,
                checkedDays = result.checkedDays,
                currentStreak = result.currentStreak,
                todayChecked = result.todayChecked,
                nextRewardPreview = RewardPreviewResponse.from(result.nextReward),
            )
    }
}
