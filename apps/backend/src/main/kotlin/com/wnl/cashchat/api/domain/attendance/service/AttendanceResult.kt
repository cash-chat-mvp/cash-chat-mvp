package com.wnl.cashchat.api.domain.attendance.service

/** 부가 보상 아이템 정의(미리보기용; 실제 인벤토리 지급 아님). */
data class BonusItem(
    val itemCode: String,
    val quantity: Int,
)

/** 특정 누적 일차의 보상 미리보기. */
data class RewardView(
    val dayCount: Int,
    val coin: Long,
    val bonusItems: List<BonusItem>,
)

/** 체크인 결과. */
data class CheckInResult(
    val awardedCoin: Long,
    val streakDayCount: Int,
    val bonusItems: List<BonusItem>,
    val nextReward: RewardView,
)

/** 월간 출석 조회 결과. */
data class MonthlyAttendance(
    val year: Int,
    val month: Int,
    val checkedDays: List<Int>,
    val currentStreak: Int,
    val todayChecked: Boolean,
    val nextReward: RewardView,
)
