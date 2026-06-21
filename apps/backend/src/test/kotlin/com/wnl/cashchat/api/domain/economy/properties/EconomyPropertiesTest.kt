package com.wnl.cashchat.api.domain.economy.properties

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import jakarta.validation.Validation
import java.math.BigDecimal

class EconomyPropertiesTest : FunSpec({
    test("defaults match spec operating parameters") {
        val p = EconomyProperties()
        p.maxEnergy shouldBe 50L
        p.energyCostPerChat shouldBe 1L
        p.chatRewardPt shouldBe 1L
        p.evolutionExpPerChat shouldBe 1L
        p.rewardedEnergyPerAd shouldBe 3L
        p.attendanceEnergyReward shouldBe 4L
        p.adEnergyExpirationDays shouldBe 30L
        p.attendanceEnergyExpirationDays shouldBe 7L
        p.rewardChatEnabled shouldBe true
        p.evolutionEnabled shouldBe true
        p.sharedPoolMarginPerChat.compareTo(BigDecimal.ZERO) shouldBe 0
    }

    test("rejects non-positive maxEnergy") {
        val validator = Validation.buildDefaultValidatorFactory().validator
        val violations = validator.validate(EconomyProperties(maxEnergy = 0L))
        violations.map { it.propertyPath.toString() } shouldContain "maxEnergy"
    }
})
