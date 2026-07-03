package com.wnl.cashchat.api.domain.ad.service

import com.wnl.cashchat.api.domain.roulette.service.RouletteService
import io.kotest.core.spec.style.FunSpec
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class AdSsvRewardRouterTest : FunSpec({
    lateinit var rouletteService: RouletteService
    lateinit var adRewardService: AdRewardService
    lateinit var router: AdSsvRewardRouter

    val now = Instant.parse("2026-06-21T03:00:00Z")
    val callback = GoogleAdSsvCallback(
        adUnit = "rewarded",
        rewardAmount = 10,
        rewardItem = "coin",
        timestamp = 1L,
        transactionId = "txn-1",
        userId = null,
        customData = "roulette-nonce",
        signature = "sig",
        keyId = 1L,
        rawQueryString = "raw",
        signedPayload = "raw",
    )

    beforeTest {
        rouletteService = mock()
        adRewardService = mock()
        router = AdSsvRewardRouter(rouletteService, adRewardService)
    }

    test("verified roulette nonce is marked for roulette and does not grant ordinary ad reward") {
        whenever(rouletteService.verifyAdNonce("roulette-nonce", "txn-1")).thenReturn(true)

        router.route(callback, now)

        verify(rouletteService).verifyAdNonce("roulette-nonce", "txn-1")
        verify(adRewardService, never()).grantFromCallback(any(), any())
    }

    test("unknown nonce falls back to ordinary ad reward grant") {
        whenever(rouletteService.verifyAdNonce("roulette-nonce", "txn-1")).thenReturn(false)

        router.route(callback, now)

        verify(adRewardService).grantFromCallback(eq(callback), eq(now))
    }
})
