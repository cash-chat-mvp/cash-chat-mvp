package com.wnl.cashchat.api.domain.quality.service

import com.wnl.cashchat.api.domain.quality.persistence.entity.DailyPremiumUsage
import com.wnl.cashchat.api.domain.quality.persistence.entity.SharedQualityPool
import com.wnl.cashchat.api.domain.quality.persistence.repository.DailyPremiumUsageRepository
import com.wnl.cashchat.api.domain.quality.persistence.repository.SharedQualityPoolRepository
import com.wnl.cashchat.api.domain.quality.properties.QualityProperties
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate

class QualityPoolServiceTest : FunSpec({
    lateinit var poolRepo: SharedQualityPoolRepository
    lateinit var dailyRepo: DailyPremiumUsageRepository
    lateinit var service: QualityPoolService

    val props = QualityProperties(poolSafetyFloorCentiPt = 500_000L, premiumDailyCapPerUser = 50)
    val userId = 42L
    val today = LocalDate.of(2026, 6, 6)

    beforeTest {
        poolRepo = mock()
        dailyRepo = mock()
        service = QualityPoolService(poolRepo, dailyRepo, props)
    }

    // ─── accrue ─────────────────────────────────────────────────────────────

    test("accrue calls pool.accrue with the given amount") {
        val pool = SharedQualityPool(balanceCentiPt = 0L)
        whenever(poolRepo.findForUpdate()).thenReturn(pool)

        service.accrue(200L)

        pool.balanceCentiPt shouldBe 200L
    }

    test("accrue throws IllegalStateException when pool row is missing") {
        whenever(poolRepo.findForUpdate()).thenReturn(null)

        val thrown = runCatching { service.accrue(100L) }.exceptionOrNull()
        (thrown is IllegalStateException) shouldBe true
    }

    // ─── tryConsumePremium ───────────────────────────────────────────────────

    test("tryConsumePremium returns true and deducts when pool is sufficient and cap not reached") {
        val pool = SharedQualityPool(balanceCentiPt = 1000L)
        whenever(poolRepo.findForUpdate()).thenReturn(pool)
        whenever(dailyRepo.findByUserIdAndUsageDate(userId, today)).thenReturn(null)

        val result = service.tryConsumePremium(userId, 300L, today)

        result shouldBe true
        pool.balanceCentiPt shouldBe 700L
        verify(dailyRepo).saveAndFlush(any())
    }

    test("tryConsumePremium returns false when pool balance is less than delta (pool gate)") {
        val pool = SharedQualityPool(balanceCentiPt = 100L)
        whenever(poolRepo.findForUpdate()).thenReturn(pool)
        whenever(dailyRepo.findByUserIdAndUsageDate(userId, today)).thenReturn(null)

        val result = service.tryConsumePremium(userId, 200L, today)

        result shouldBe false
        pool.balanceCentiPt shouldBe 100L
        verify(dailyRepo, never()).saveAndFlush(any())
    }

    test("tryConsumePremium returns false when daily cap is reached without touching pool balance") {
        val pool = SharedQualityPool(balanceCentiPt = 1000L)
        val cappedUsage = DailyPremiumUsage(userId = userId, usageDate = today, count = 50)
        whenever(poolRepo.findForUpdate()).thenReturn(pool)
        whenever(dailyRepo.findByUserIdAndUsageDate(userId, today)).thenReturn(cappedUsage)

        val result = service.tryConsumePremium(userId, 300L, today)

        result shouldBe false
        // Pool lock IS acquired (to serialize concurrent requests) but balance must be untouched
        verify(poolRepo).findForUpdate()
        pool.balanceCentiPt shouldBe 1000L
        verify(dailyRepo, never()).saveAndFlush(any())
    }

    test("tryConsumePremium increments existing usage row when cap is not reached") {
        val pool = SharedQualityPool(balanceCentiPt = 1000L)
        val existingUsage = DailyPremiumUsage(userId = userId, usageDate = today, count = 5)
        whenever(poolRepo.findForUpdate()).thenReturn(pool)
        whenever(dailyRepo.findByUserIdAndUsageDate(userId, today)).thenReturn(existingUsage)

        val result = service.tryConsumePremium(userId, 300L, today)

        result shouldBe true
        existingUsage.count shouldBe 6
        verify(dailyRepo, never()).saveAndFlush(any())
    }

    // ─── throttleScale ───────────────────────────────────────────────────────

    test("throttleScale returns 0.5 when balance is half the safety floor") {
        val pool = SharedQualityPool(balanceCentiPt = 250_000L)
        whenever(poolRepo.findById1()).thenReturn(pool)

        service.throttleScale() shouldBe (0.5 plusOrMinus 1e-9)
    }

    test("throttleScale returns 1.0 when balance meets the safety floor") {
        val pool = SharedQualityPool(balanceCentiPt = 500_000L)
        whenever(poolRepo.findById1()).thenReturn(pool)

        service.throttleScale() shouldBe (1.0 plusOrMinus 1e-9)
    }

    test("throttleScale returns 1.0 when balance exceeds the safety floor") {
        val pool = SharedQualityPool(balanceCentiPt = 800_000L)
        whenever(poolRepo.findById1()).thenReturn(pool)

        service.throttleScale() shouldBe (1.0 plusOrMinus 1e-9)
    }

    test("throttleScale returns 0.0 when pool row is missing") {
        whenever(poolRepo.findById1()).thenReturn(null)

        service.throttleScale() shouldBe 0.0
    }
})
