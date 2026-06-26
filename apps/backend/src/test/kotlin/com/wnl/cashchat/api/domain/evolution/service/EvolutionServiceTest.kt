package com.wnl.cashchat.api.domain.evolution.service

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.energy.service.EnergyService
import com.wnl.cashchat.api.domain.evolution.exception.AlreadyMaxLevelException
import com.wnl.cashchat.api.domain.evolution.exception.InsufficientEvolutionExpException
import com.wnl.cashchat.api.domain.evolution.persistence.entity.EvolutionAttempt
import com.wnl.cashchat.api.domain.evolution.persistence.entity.UserEvolution
import com.wnl.cashchat.api.domain.evolution.persistence.repository.EvolutionAttemptRepository
import com.wnl.cashchat.api.domain.evolution.persistence.repository.UserEvolutionRepository
import com.wnl.cashchat.api.domain.evolution.properties.EvolutionProperties
import com.wnl.cashchat.api.domain.evolution.properties.EvolutionProperties.LevelRule
import com.wnl.cashchat.api.domain.user.persistence.entity.Role
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class EvolutionServiceTest : FunSpec({
    lateinit var userEvolutionRepository: UserEvolutionRepository
    lateinit var evolutionAttemptRepository: EvolutionAttemptRepository
    lateinit var probabilityRoller: ProbabilityRoller
    lateinit var energyService: EnergyService
    lateinit var service: EvolutionService

    val userId = 1L
    fun user() = User(id = userId, role = Role.GUEST, provider = AuthProviderType.NONE, name = "Guest")

    /** 진화 경험치를 미리 적립한 상태의 UserEvolution 을 만든다(R2: 시도 비용을 exp 로 차감). */
    fun evo(level: Int = 1, exp: Long = 10_000L) = UserEvolution(user = user(), level = level).apply { addExp(exp) }

    val properties = EvolutionProperties(
        rules = listOf(
            LevelRule(fromLevel = 1, attemptCost = 500, successRate = 0.7),
            LevelRule(fromLevel = 2, attemptCost = 1200, successRate = 0.5),
        ),
    )

    beforeTest {
        userEvolutionRepository = mock()
        evolutionAttemptRepository = mock()
        probabilityRoller = mock()
        energyService = mock()
        service = EvolutionService(
            userEvolutionRepository,
            evolutionAttemptRepository,
            probabilityRoller,
            properties,
            energyService,
        )
        whenever(evolutionAttemptRepository.findByUserIdAndIdempotencyKey(any(), any())).thenReturn(null)
    }

    test("getState returns current level and next transition rule") {
        whenever(userEvolutionRepository.findByUserId(userId)).thenReturn(UserEvolution(user = user(), level = 1))

        val state = service.getState(userId)

        state.level shouldBe 1
        state.isMaxLevel shouldBe false
        state.nextAttemptCost shouldBe 500L
        state.nextSuccessRate shouldBe 0.7
    }

    test("getState at a level with no rule is reported as max") {
        whenever(userEvolutionRepository.findByUserId(userId)).thenReturn(UserEvolution(user = user(), level = 3))

        val state = service.getState(userId)

        state.isMaxLevel shouldBe true
        state.nextAttemptCost shouldBe null
        state.nextSuccessRate shouldBe null
    }

    test("successful attempt deducts exp, levels up, and logs success") {
        val evolution = evo(level = 1, exp = 1000L)
        whenever(userEvolutionRepository.findByUserIdForUpdate(userId)).thenReturn(evolution)
        whenever(probabilityRoller.succeeds(0.7)).thenReturn(true)

        val result = service.attempt(userId, "key-1")

        result.success shouldBe true
        result.fromLevel shouldBe 1
        result.resultLevel shouldBe 2
        result.cost shouldBe 500L
        evolution.exp shouldBe 500L
        verify(evolutionAttemptRepository).save(argThat<EvolutionAttempt> {
            this.userId == userId && fromLevel == 1 && success && resultLevel == 2 && idempotencyKey == "key-1"
        })
        verify(energyService).applyPostEvolutionBoost(userId)
    }

    test("failed attempt still deducts exp but does not level up") {
        val evolution = evo(level = 1, exp = 1000L)
        whenever(userEvolutionRepository.findByUserIdForUpdate(userId)).thenReturn(evolution)
        whenever(probabilityRoller.succeeds(0.7)).thenReturn(false)

        val result = service.attempt(userId, "key-2")

        result.success shouldBe false
        result.fromLevel shouldBe 1
        result.resultLevel shouldBe 1
        evolution.exp shouldBe 500L
        verify(evolutionAttemptRepository).save(argThat<EvolutionAttempt> {
            !success && resultLevel == 1
        })
        verify(energyService, never()).applyPostEvolutionBoost(any())
    }

    test("attempt at a level with no rule throws AlreadyMaxLevel and never charges exp") {
        val evolution = evo(level = 3, exp = 1000L)
        whenever(userEvolutionRepository.findByUserIdForUpdate(userId)).thenReturn(evolution)

        shouldThrow<AlreadyMaxLevelException> { service.attempt(userId, "key-3") }

        evolution.exp shouldBe 1000L
        verify(evolutionAttemptRepository, never()).save(any())
        verify(energyService, never()).applyPostEvolutionBoost(any())
    }

    test("duplicate idempotency key returns the prior attempt without charging again") {
        val evolution = evo(level = 1, exp = 1000L)
        whenever(userEvolutionRepository.findByUserIdForUpdate(userId)).thenReturn(evolution)
        whenever(evolutionAttemptRepository.findByUserIdAndIdempotencyKey(userId, "key-4")).thenReturn(
            EvolutionAttempt(userId = userId, fromLevel = 1, cost = 500, success = true, resultLevel = 2, idempotencyKey = "key-4")
        )

        val result = service.attempt(userId, "key-4")

        result.success shouldBe true
        result.resultLevel shouldBe 2
        evolution.exp shouldBe 1000L
        verify(evolutionAttemptRepository, never()).save(any())
        verify(energyService, never()).applyPostEvolutionBoost(any())
    }

    test("insufficient exp propagates and does not level up or log") {
        val evolution = evo(level = 1, exp = 100L)
        whenever(userEvolutionRepository.findByUserIdForUpdate(userId)).thenReturn(evolution)

        shouldThrow<InsufficientEvolutionExpException> { service.attempt(userId, "key-5") }

        evolution.exp shouldBe 100L
        verify(probabilityRoller, never()).succeeds(any())
        verify(evolutionAttemptRepository, never()).save(any())
        verify(energyService, never()).applyPostEvolutionBoost(any())
    }

    test("getState exposes current evolution exp") {
        val evolution = UserEvolution(user = user(), level = 1).apply { addExp(750L) }
        whenever(userEvolutionRepository.findByUserId(userId)).thenReturn(evolution)

        val state = service.getState(userId)

        state.currentExp shouldBe 750L
    }

    test("getAttempts returns own records newest-first limited by limit") {
        val now = java.time.Instant.parse("2026-06-25T12:34:56Z")
        val a1 = EvolutionAttempt(userId = userId, fromLevel = 2, cost = 1200, success = true, resultLevel = 3, idempotencyKey = "k1")
            .apply { createdAt = now }
        whenever(evolutionAttemptRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), any()))
            .thenReturn(listOf(a1))

        val records = service.getAttempts(userId, 20)

        records.size shouldBe 1
        records[0].success shouldBe true
        records[0].fromLevel shouldBe 2
        records[0].resultLevel shouldBe 3
        records[0].cost shouldBe 1200L
        records[0].attemptedAt shouldBe now
    }
})
