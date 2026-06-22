package com.wnl.cashchat.api.domain.economy.service

import com.wnl.cashchat.api.domain.economy.properties.EconomyProperties
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.math.BigDecimal

class ModelRoutingServiceTest : FunSpec({
    test("routing disabled -> NANO, no pool consumption") {
        val pool = mock<SharedQualityPoolService>()
        val props = EconomyProperties(
            premiumRoutingEnabled = false, premiumDeltaPt = BigDecimal("2.0"),
            nanoModelName = "nano-1", premiumModelName = "pro-1",
        )
        val d = ModelRoutingService(pool, props).selectAndConsume()
        d.tier shouldBe ModelTier.NANO
        d.modelOverride shouldBe "nano-1"
        verifyNoInteractions(pool)
    }

    test("routing enabled + pool sufficient -> PREMIUM") {
        val pool = mock<SharedQualityPoolService>()
        whenever(pool.tryConsumePremium(any())).thenReturn(true)
        val props = EconomyProperties(
            premiumRoutingEnabled = true, premiumDeltaPt = BigDecimal("2.0"),
            nanoModelName = "nano-1", premiumModelName = "pro-1",
        )
        val d = ModelRoutingService(pool, props).selectAndConsume()
        d.tier shouldBe ModelTier.PREMIUM
        d.modelOverride shouldBe "pro-1"
    }

    test("routing enabled + pool insufficient -> NANO downgrade") {
        val pool = mock<SharedQualityPoolService>()
        whenever(pool.tryConsumePremium(any())).thenReturn(false)
        val props = EconomyProperties(
            premiumRoutingEnabled = true, premiumDeltaPt = BigDecimal("2.0"),
            nanoModelName = "nano-1", premiumModelName = "pro-1",
        )
        val d = ModelRoutingService(pool, props).selectAndConsume()
        d.tier shouldBe ModelTier.NANO
        d.modelOverride shouldBe "nano-1"
    }

    test("blank model name maps to null override") {
        val pool = mock<SharedQualityPoolService>()
        val d = ModelRoutingService(pool, EconomyProperties(premiumRoutingEnabled = false)).selectAndConsume()
        d.modelOverride shouldBe null
    }
})
