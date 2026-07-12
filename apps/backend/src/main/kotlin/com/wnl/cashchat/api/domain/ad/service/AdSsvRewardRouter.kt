package com.wnl.cashchat.api.domain.ad.service

import com.wnl.cashchat.api.domain.roulette.service.RouletteService
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class AdSsvRewardRouter(
    private val rouletteService: RouletteService,
    private val adRewardService: AdRewardService,
) {
    fun route(callback: GoogleAdSsvCallback, now: Instant) {
        if (rouletteService.verifyAdNonce(callback.customData, callback.transactionId)) {
            return
        }
        adRewardService.grantFromCallback(callback, now)
    }
}
