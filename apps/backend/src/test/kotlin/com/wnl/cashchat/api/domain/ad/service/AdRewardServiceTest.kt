package com.wnl.cashchat.api.domain.ad.service

import com.wnl.cashchat.api.domain.ad.persistence.entity.AdRewardDailyQuota
import com.wnl.cashchat.api.domain.ad.persistence.entity.AdRewardNonce
import com.wnl.cashchat.api.domain.ad.persistence.entity.GoogleAdSsvEvent
import com.wnl.cashchat.api.domain.ad.persistence.entity.RewardStatus
import com.wnl.cashchat.api.domain.ad.persistence.repository.AdRewardDailyQuotaRepository
import com.wnl.cashchat.api.domain.ad.persistence.repository.AdRewardNonceRepository
import com.wnl.cashchat.api.domain.ad.persistence.repository.GoogleAdSsvEventRepository
import com.wnl.cashchat.api.domain.ad.properties.AdRewardProperties
import com.wnl.cashchat.api.domain.economy.persistence.entity.EnergySourceType
import com.wnl.cashchat.api.domain.economy.properties.EconomyProperties
import com.wnl.cashchat.api.domain.economy.service.EnergyService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.LocalDate

class AdRewardServiceTest : FunSpec({
    lateinit var eventRepository: GoogleAdSsvEventRepository
    lateinit var nonceRepository: AdRewardNonceRepository
    lateinit var quotaRepository: AdRewardDailyQuotaRepository
    lateinit var energyService: EnergyService
    lateinit var service: AdRewardService

    val now = Instant.parse("2026-05-31T00:00:00Z")
    val kstToday = LocalDate.of(2026, 5, 31)
    val txnId = "txn-1"

    fun callback(userIdNonce: String) = GoogleAdSsvCallback(
        adUnit = "rewarded", rewardAmount = 10, rewardItem = "coin", timestamp = 1L,
        transactionId = txnId, userId = userIdNonce, signature = "sig", keyId = 1L,
        rawQueryString = "raw", signedPayload = "raw",
    )

    beforeTest {
        eventRepository = mock()
        nonceRepository = mock()
        quotaRepository = mock()
        energyService = mock()
        service = AdRewardService(eventRepository, nonceRepository, quotaRepository, energyService, AdRewardProperties(), EconomyProperties())
    }

    test("invalid/used/expired nonce marks event REJECTED_INVALID_NONCE and grants nothing") {
        val event = GoogleAdSsvEvent(transactionId = txnId, userId = "nonce-x", rewardAmount = 10, rewardItem = "coin", adUnit = "rewarded", keyId = 1L, rawQueryString = "raw")
        whenever(eventRepository.findForUpdateByTransactionId(txnId)).thenReturn(event)
        whenever(nonceRepository.findForUpdate("nonce-x")).thenReturn(null)

        service.grantFromCallback(callback("nonce-x"), now)

        event.rewardStatus shouldBe RewardStatus.REJECTED_INVALID_NONCE
        verify(energyService, never()).grant(any(), any(), any(), any(), any())
    }

    test("over quota marks event REJECTED_OVER_QUOTA, consumes nonce, and grants nothing") {
        val event = GoogleAdSsvEvent(transactionId = txnId, userId = "nonce-y", rewardAmount = 10, rewardItem = "coin", adUnit = "rewarded", keyId = 1L, rawQueryString = "raw")
        val nonce = AdRewardNonce(nonce = "nonce-y", userId = 7L, expiresAt = now.plusSeconds(60))
        whenever(eventRepository.findForUpdateByTransactionId(txnId)).thenReturn(event)
        whenever(nonceRepository.findForUpdate("nonce-y")).thenReturn(nonce)
        whenever(quotaRepository.findForUpdate(7L, kstToday)).thenReturn(AdRewardDailyQuota(userId = 7L, kstDate = kstToday, usedCount = 10))

        service.grantFromCallback(callback("nonce-y"), now)

        event.rewardStatus shouldBe RewardStatus.REJECTED_OVER_QUOTA
        // 한도 초과 거절이어도 유효 nonce 는 소모되어 단일 사용이 보장된다.
        nonce.used shouldBe true
        verify(energyService, never()).grant(any(), any(), any(), any(), any())
    }

    test("valid nonce within quota grants energy, marks used, increments quota, event GRANTED") {
        val event = GoogleAdSsvEvent(transactionId = txnId, userId = "nonce-z", rewardAmount = 10, rewardItem = "coin", adUnit = "rewarded", keyId = 1L, rawQueryString = "raw")
        val nonce = AdRewardNonce(nonce = "nonce-z", userId = 7L, expiresAt = now.plusSeconds(60))
        val quota = AdRewardDailyQuota(userId = 7L, kstDate = kstToday, usedCount = 3)
        whenever(eventRepository.findForUpdateByTransactionId(txnId)).thenReturn(event)
        whenever(nonceRepository.findForUpdate("nonce-z")).thenReturn(nonce)
        whenever(quotaRepository.findForUpdate(7L, kstToday)).thenReturn(quota)

        service.grantFromCallback(callback("nonce-z"), now)

        event.rewardStatus shouldBe RewardStatus.GRANTED
        nonce.used shouldBe true
        quota.usedCount shouldBe 4
        verify(energyService).grant(eq(7L), eq(3L), eq(EnergySourceType.REWARDED_AD), any(), eq("admob:reward:txn-1"))
    }

    test("already GRANTED event is skipped idempotently (no re-grant, no quota touch)") {
        val event = GoogleAdSsvEvent(transactionId = txnId, userId = "nonce-z", rewardAmount = 10, rewardItem = "coin", adUnit = "rewarded", keyId = 1L, rawQueryString = "raw")
        event.markGranted()
        whenever(eventRepository.findForUpdateByTransactionId(txnId)).thenReturn(event)

        service.grantFromCallback(callback("nonce-z"), now)

        event.rewardStatus shouldBe RewardStatus.GRANTED
        verify(nonceRepository, never()).findForUpdate(any())
        verify(energyService, never()).grant(any(), any(), any(), any(), any())
    }

    test("already REJECTED event is skipped on retry (no nonce lock, no re-grant)") {
        val event = GoogleAdSsvEvent(transactionId = txnId, userId = "nonce-z", rewardAmount = 10, rewardItem = "coin", adUnit = "rewarded", keyId = 1L, rawQueryString = "raw")
        event.markRejected(RewardStatus.REJECTED_OVER_QUOTA)
        whenever(eventRepository.findForUpdateByTransactionId(txnId)).thenReturn(event)

        service.grantFromCallback(callback("nonce-z"), now)

        // 거절 종결 상태는 재전송돼도 그대로 유지되고, nonce 락 획득·적립을 시도하지 않는다.
        event.rewardStatus shouldBe RewardStatus.REJECTED_OVER_QUOTA
        verify(nonceRepository, never()).findForUpdate(any())
        verify(energyService, never()).grant(any(), any(), any(), any(), any())
    }
})
