package com.wnl.cashchat.api.domain.evolution.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ProbabilityRollerTest : FunSpec({
    val roller = SecureRandomProbabilityRoller()

    test("rate 0.0 never succeeds") {
        repeat(1000) { roller.succeeds(0.0) shouldBe false }
    }

    test("rate 1.0 always succeeds") {
        repeat(1000) { roller.succeeds(1.0) shouldBe true }
    }
})