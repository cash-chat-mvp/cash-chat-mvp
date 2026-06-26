package com.wnl.cashchat.api.domain.evolution.service

import com.wnl.cashchat.api.domain.evolution.exception.InvalidTimingSessionException
import com.wnl.cashchat.api.domain.evolution.properties.EvolutionProperties
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class EvolutionTimingJudgeTest : FunSpec({
    // cycleDurationMs = 1800. position = (releasedAtMs % 1800) / 1800.
    val judge = EvolutionTimingJudge(EvolutionProperties().timing)

    test("center release is PERFECT and adds 0.10") {
        // position 0.5 → releasedAtMs = 900 (in [0.45,0.55])
        val j = judge.judge(releasedAtMs = 900, elapsedSinceStartMs = 1000, baseSuccessRate = 0.65)
        j.grade shouldBe TimingGrade.PERFECT
        j.bonusRate shouldBe 0.10
        j.baseSuccessRate shouldBe 0.65
        j.finalSuccessRate shouldBe 0.75
    }

    test("great band release is GREAT (+0.05)") {
        // position 0.40 → releasedAtMs = 720 (in [0.38,0.62] but not perfect)
        val j = judge.judge(releasedAtMs = 720, elapsedSinceStartMs = 1000, baseSuccessRate = 0.5)
        j.grade shouldBe TimingGrade.GREAT
        j.finalSuccessRate shouldBe 0.55
    }

    test("outside band is NORMAL (+0.0)") {
        // position 0.10 → releasedAtMs = 180
        val j = judge.judge(releasedAtMs = 180, elapsedSinceStartMs = 1000, baseSuccessRate = 0.5)
        j.grade shouldBe TimingGrade.NORMAL
        j.finalSuccessRate shouldBe 0.5
    }

    test("final success rate is capped at 1.0") {
        val j = judge.judge(releasedAtMs = 900, elapsedSinceStartMs = 1000, baseSuccessRate = 0.95)
        j.finalSuccessRate shouldBe 1.0
    }

    test("releasedAtMs beyond elapsed + tolerance is rejected (tamper)") {
        // elapsed 500, tolerance 2000 → max 2500; 3000 exceeds
        shouldThrow<InvalidTimingSessionException> {
            judge.judge(releasedAtMs = 3000, elapsedSinceStartMs = 500, baseSuccessRate = 0.5)
        }
    }

    test("negative releasedAtMs is rejected") {
        shouldThrow<InvalidTimingSessionException> {
            judge.judge(releasedAtMs = -1, elapsedSinceStartMs = 1000, baseSuccessRate = 0.5)
        }
    }
})
