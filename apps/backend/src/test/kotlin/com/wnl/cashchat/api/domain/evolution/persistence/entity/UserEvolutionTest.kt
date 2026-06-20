package com.wnl.cashchat.api.domain.evolution.persistence.entity

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.user.persistence.entity.Role
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class UserEvolutionTest : FunSpec({
    fun user() = User(id = 1L, role = Role.GUEST, provider = AuthProviderType.NONE, name = "Guest")

    test("new evolution starts at level 1 and is not max") {
        val evo = UserEvolution(user = user())
        evo.level shouldBe 1
        evo.isMaxLevel() shouldBe false
    }

    test("levelUp increments the level") {
        val evo = UserEvolution(user = user(), level = 2)
        evo.levelUp()
        evo.level shouldBe 3
    }

    test("levelUp at MAX_LEVEL throws and keeps the level") {
        val evo = UserEvolution(user = user(), level = UserEvolution.MAX_LEVEL)
        shouldThrow<IllegalStateException> { evo.levelUp() }
        evo.level shouldBe UserEvolution.MAX_LEVEL
    }

    test("constructor rejects a level below 1") {
        shouldThrow<IllegalArgumentException> { UserEvolution(user = user(), level = 0) }
    }
})