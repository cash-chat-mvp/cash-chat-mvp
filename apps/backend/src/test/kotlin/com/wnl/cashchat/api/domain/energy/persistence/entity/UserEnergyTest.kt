package com.wnl.cashchat.api.domain.energy.persistence.entity

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.user.persistence.entity.Role
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class UserEnergyTest : FunSpec({
    fun user() = User(id = 1L, role = Role.GUEST, provider = AuthProviderType.NONE, name = "Guest")

    test("charge adds energy but never exceeds maxEnergy") {
        val e = UserEnergy(user = user(), energy = 48)
        e.charge(amount = 5, maxEnergy = 50)
        e.energy shouldBe 50
    }

    test("consume decrements by one") {
        val e = UserEnergy(user = user(), energy = 3)
        e.consume()
        e.energy shouldBe 2
    }

    test("consume at zero throws and keeps energy") {
        val e = UserEnergy(user = user(), energy = 0)
        shouldThrow<IllegalStateException> { e.consume() }
        e.energy shouldBe 0
    }

    test("boostTo raises energy up to the floor but never lowers it") {
        val low = UserEnergy(user = user(), energy = 10)
        low.boostTo(25)
        low.energy shouldBe 25

        val high = UserEnergy(user = user(), energy = 40)
        high.boostTo(25)
        high.energy shouldBe 40
    }

    test("constructor rejects negative energy") {
        shouldThrow<IllegalArgumentException> { UserEnergy(user = user(), energy = -1) }
    }

    test("reserve moves one from available to reserved") {
        val e = UserEnergy(user = user(), energy = 3)
        e.reserve()
        e.energy shouldBe 2
        e.reservedEnergy shouldBe 1
    }

    test("reserve at zero available throws and keeps state") {
        val e = UserEnergy(user = user(), energy = 0)
        shouldThrow<IllegalStateException> { e.reserve() }
        e.energy shouldBe 0
        e.reservedEnergy shouldBe 0
    }

    test("settleReserved consumes one reserved (available unchanged)") {
        val e = UserEnergy(user = user(), energy = 3)
        e.reserve()
        e.settleReserved()
        e.energy shouldBe 2
        e.reservedEnergy shouldBe 0
    }

    test("refundReserved returns one reserved to available") {
        val e = UserEnergy(user = user(), energy = 3)
        e.reserve()
        e.refundReserved()
        e.energy shouldBe 3
        e.reservedEnergy shouldBe 0
    }

    test("settleReserved with nothing reserved throws") {
        val e = UserEnergy(user = user(), energy = 3)
        shouldThrow<IllegalStateException> { e.settleReserved() }
    }

    test("refundReserved with nothing reserved throws") {
        val e = UserEnergy(user = user(), energy = 3)
        shouldThrow<IllegalStateException> { e.refundReserved() }
    }
})
