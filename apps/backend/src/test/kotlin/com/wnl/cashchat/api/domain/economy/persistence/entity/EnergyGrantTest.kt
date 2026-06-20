package com.wnl.cashchat.api.domain.economy.persistence.entity

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class EnergyGrantTest : FunSpec({
    test("consume takes up to remaining and reports the taken amount") {
        val g = EnergyGrant(1L, EnergySourceType.REWARDED_AD, 3, Instant.now(), Instant.now())
        g.consume(2) shouldBe 2L
        g.remainingAmount shouldBe 1L
        g.consume(5) shouldBe 1L   // only 1 left
        g.remainingAmount shouldBe 0L
    }
})
