package com.wnl.cashchat.api.domain.economy.persistence.entity

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.economy.exception.EnergyCapExceededException
import com.wnl.cashchat.api.domain.user.persistence.entity.Role
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class UserWalletTest : FunSpec({
    fun wallet() = UserWallet(
        user = User(id = 1L, role = Role.MEMBER, provider = AuthProviderType.NONE, name = "w")
    )

    test("grantEnergy increases available up to max") {
        val w = wallet(); w.grantEnergy(3, maxEnergy = 50); w.energyAvailable shouldBe 3L
    }
    test("grantEnergy beyond max is rejected") {
        val w = wallet(); w.grantEnergy(48, maxEnergy = 50)
        shouldThrow<EnergyCapExceededException> { w.grantEnergy(3, maxEnergy = 50) }
        w.energyAvailable shouldBe 48L
    }
    test("reserve then consume moves energy out of the wallet") {
        val w = wallet(); w.grantEnergy(2, maxEnergy = 50)
        w.reserveEnergy(); w.energyAvailable shouldBe 1L; w.energyReserved shouldBe 1L
        w.consumeReserved(); w.energyReserved shouldBe 0L; w.energyAvailable shouldBe 1L
    }
    test("reserve fails when no available energy") {
        shouldThrow<IllegalArgumentException> { wallet().reserveEnergy() }
    }
    test("refund returns reserved energy to available") {
        val w = wallet(); w.grantEnergy(1, maxEnergy = 50); w.reserveEnergy(); w.refundReserved()
        w.energyAvailable shouldBe 1L; w.energyReserved shouldBe 0L
    }
    test("pending points can be confirmed") {
        val w = wallet(); w.addPendingPt(5); w.confirmPending(5)
        w.pendingCashablePt shouldBe 0L; w.confirmedCashablePt shouldBe 5L
    }
    test("addExp accumulates evolution exp") {
        val w = wallet(); w.addExp(1); w.addExp(1); w.evolutionExp shouldBe 2L
    }
    test("confirmPending rejects confirming more than pending") {
        shouldThrow<IllegalArgumentException> { wallet().confirmPending(1) }
    }
    test("mutators reject negative amounts") {
        shouldThrow<IllegalArgumentException> { wallet().grantEnergy(-1, maxEnergy = 50) }
        shouldThrow<IllegalArgumentException> { wallet().reserveEnergy(-1) }
        shouldThrow<IllegalArgumentException> { wallet().addPendingPt(-1) }
        shouldThrow<IllegalArgumentException> { wallet().addExp(-1) }
    }
})
