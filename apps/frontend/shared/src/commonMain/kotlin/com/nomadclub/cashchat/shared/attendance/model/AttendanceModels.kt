package com.nomadclub.cashchat.shared.attendance.model

import kotlinx.serialization.Serializable

@Serializable
data class BonusItem(val itemCode: String, val quantity: Int)

@Serializable
data class RewardPreview(
    val dayCount: Int,
    val coin: Long,
    val bonusItems: List<BonusItem>,
)

@Serializable
data class MonthlyAttendance(
    val year: Int,
    val month: Int,
    val checkedDays: List<Int>,
    val currentStreak: Int,
    val todayChecked: Boolean,
    val nextRewardPreview: RewardPreview,
)

@Serializable
data class CheckInResult(
    val awardedCoin: Long,
    val streakDayCount: Int,
    val bonusItems: List<BonusItem>,
    val nextRewardPreview: RewardPreview,
)
