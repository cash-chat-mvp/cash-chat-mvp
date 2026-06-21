package com.wnl.cashchat.api.domain.economy

import com.wnl.cashchat.api.domain.economy.properties.EvolutionProperties
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class EvolutionPropertiesTest : FunSpec({
    val props = EvolutionProperties()

    test("defaults match Confluence CC-283 numbers") {
        props.maxLevel shouldBe 5
        props.policyVersion shouldBe 1
        props.failStackBonus shouldBe 0.10
        props.policyFor(1) shouldBe EvolutionProperties.LevelPolicy(1, 30, 0.80, 0.0)
        props.policyFor(2) shouldBe EvolutionProperties.LevelPolicy(2, 100, 0.60, 0.0)
        props.policyFor(3) shouldBe EvolutionProperties.LevelPolicy(3, 300, 0.35, 0.20)
        props.policyFor(4) shouldBe EvolutionProperties.LevelPolicy(4, 1000, 0.15, 0.30)
    }

    test("policyFor returns null at and beyond max level") {
        props.policyFor(5) shouldBe null
    }
})
