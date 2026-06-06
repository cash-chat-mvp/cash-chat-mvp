package com.wnl.cashchat.api.domain.ad.service

import java.time.Instant

data class AdRewardQuota(
    val usedToday: Int,
    val dailyLimit: Int,
    val remaining: Int,
    val resetAtKst: Instant,
)
