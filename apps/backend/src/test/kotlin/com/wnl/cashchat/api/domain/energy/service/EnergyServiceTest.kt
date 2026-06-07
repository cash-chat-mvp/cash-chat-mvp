package com.wnl.cashchat.api.domain.energy.service

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.energy.exception.InsufficientEnergyException
import com.wnl.cashchat.api.domain.energy.persistence.entity.UserEnergy
import com.wnl.cashchat.api.domain.energy.persistence.repository.UserEnergyRepository
import com.wnl.cashchat.api.domain.energy.properties.EnergyProperties
import com.wnl.cashchat.api.domain.user.persistence.entity.Role
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class EnergyServiceTest : FunSpec({
    lateinit var userEnergyRepository: UserEnergyRepository
    lateinit var service: EnergyService

    val userId = 1L
    fun user() = User(id = userId, role = Role.GUEST, provider = AuthProviderType.NONE, name = "Guest")
    val properties = EnergyProperties(maxEnergy = 50, signupBonus = 50, postEvolutionRatio = 0.5)

    beforeTest {
        userEnergyRepository = mock()
        service = EnergyService(userEnergyRepository, properties)
    }

    test("getEnergy returns current energy and configured max") {
        whenever(userEnergyRepository.findByUserId(userId)).thenReturn(UserEnergy(user = user(), energy = 12))
        val view = service.getEnergy(userId)
        view.energy shouldBe 12
        view.maxEnergy shouldBe 50
    }

    test("consume decrements energy when available") {
        val energy = UserEnergy(user = user(), energy = 3)
        whenever(userEnergyRepository.findByUserIdForUpdate(userId)).thenReturn(energy)
        service.consume(userId)
        energy.energy shouldBe 2
    }

    test("consume at zero throws InsufficientEnergyException (gate)") {
        whenever(userEnergyRepository.findByUserIdForUpdate(userId)).thenReturn(UserEnergy(user = user(), energy = 0))
        shouldThrow<InsufficientEnergyException> { service.consume(userId) }
    }

    test("charge adds energy capped at max") {
        val energy = UserEnergy(user = user(), energy = 48)
        whenever(userEnergyRepository.findByUserIdForUpdate(userId)).thenReturn(energy)
        service.charge(userId, 5)
        energy.energy shouldBe 50
    }

    test("applyPostEvolutionBoost raises energy to floor(max*ratio)") {
        val energy = UserEnergy(user = user(), energy = 10)
        whenever(userEnergyRepository.findByUserIdForUpdate(userId)).thenReturn(energy)
        service.applyPostEvolutionBoost(userId)
        energy.energy shouldBe 25 // floor(50*0.5)
    }
})
