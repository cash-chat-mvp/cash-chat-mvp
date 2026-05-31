package com.wnl.cashchat.api.domain.ad.web.response

import com.wnl.cashchat.api.domain.ad.service.AdRewardQuota
import java.time.Instant

data class AdRewardQuotaResponse(
    val usedToday: Int,
    val dailyLimit: Int,
    val remaining: Int,
    val resetAtKst: Instant,
) {
    companion object {
        fun from(quota: AdRewardQuota): AdRewardQuotaResponse =
            AdRewardQuotaResponse(quota.usedToday, quota.dailyLimit, quota.remaining, quota.resetAtKst)
    }
}
