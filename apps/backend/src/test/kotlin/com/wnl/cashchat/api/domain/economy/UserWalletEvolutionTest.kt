package com.wnl.cashchat.api.domain.economy

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.economy.persistence.entity.UserWallet
import com.wnl.cashchat.api.domain.user.persistence.entity.Role
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class UserWalletEvolutionTest : FunSpec({
    fun wallet(): UserWallet = UserWallet(
        user = User(id = 1L, role = Role.MEMBER, provider = AuthProviderType.NONE, name = "w")
    )

    test("success raises level and resets exp and failStack") {
        val w = wallet()
        w.addExp(120); repeat(3) { w.applyEvolutionFailure(0.0) } // failStack=3, exp=0
        w.addExp(120)
        w.applyEvolutionSuccess()
        w.evolutionLevel shouldBe 2
        w.evolutionExp shouldBe 0
        w.evolutionFailStack shouldBe 0
    }

    test("failure keeps level, floors exp by ratio, increments failStack") {
        val w = wallet()
        w.addExp(301)
        w.applyEvolutionFailure(0.20)
        w.evolutionLevel shouldBe 1
        w.evolutionExp shouldBe 60   // floor(301 * 0.20) = 60
        w.evolutionFailStack shouldBe 1
    }

    test("failure with zero keep ratio resets exp to zero") {
        val w = wallet()
        w.addExp(100)
        w.applyEvolutionFailure(0.0)
        w.evolutionExp shouldBe 0
        w.evolutionFailStack shouldBe 1
    }
})
