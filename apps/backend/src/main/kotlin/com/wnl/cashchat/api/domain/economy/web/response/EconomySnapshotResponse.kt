package com.wnl.cashchat.api.domain.economy.web.response

import java.time.Instant

data class EconomySnapshotResponse(
    val serverTime: Instant,
    val energy: EnergyView,
    val point: PointView,
    val evolution: EvolutionView,
    val ad: AdView,
    val features: FeaturesView,
) {
    data class EnergyView(val available: Long, val reserved: Long, val max: Long)
    data class PointView(val pending: Long, val confirmed: Long)
    data class EvolutionView(val level: Int, val exp: Long, val failStack: Int)
    data class AdView(val usedToday: Int, val dailyLimit: Int, val remaining: Int, val resetAt: Instant)
    data class FeaturesView(
        val rewardChatEnabled: Boolean,
        val rewardedAdEnabled: Boolean,
        val attendanceRewardEnabled: Boolean,
        val evolutionEnabled: Boolean,
        val cashoutEnabled: Boolean,
    )
}
