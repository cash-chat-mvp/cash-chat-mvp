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
import com.wnl.cashchat.api.domain.user.persistence.repository.UserRepository
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional

class EvolutionServiceTest : FunSpec({
    lateinit var userEvolutionRepository: UserEvolutionRepository
    lateinit var evolutionAttemptRepository: EvolutionAttemptRepository
    lateinit var userRepository: UserRepository
    lateinit var probabilityRoller: ProbabilityRoller
    lateinit var energyService: EnergyService
    lateinit var timingSessionStore: TimingSessionStore
    lateinit var evolutionTimingJudge: EvolutionTimingJudge
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
        userRepository = mock()
        probabilityRoller = mock()
        energyService = mock()
        timingSessionStore = mock()
        evolutionTimingJudge = mock()
        service = EvolutionService(
            userEvolutionRepository,
            evolutionAttemptRepository,
            userRepository,
            probabilityRoller,
            properties,
            energyService,
            timingSessionStore,
            evolutionTimingJudge,
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

    test("getState initializes evolution state for an existing user when missing") {
        val user = user()
        whenever(userEvolutionRepository.findByUserId(userId)).thenReturn(null)
        whenever(userRepository.findById(userId)).thenReturn(Optional.of(user))
        doAnswer { it.arguments[0] as UserEvolution }
            .whenever(userEvolutionRepository).saveAndFlush(any())

        val state = service.getState(userId)

        state.level shouldBe 1
        state.currentExp shouldBe 0L
        verify(userEvolutionRepository).saveAndFlush(argThat<UserEvolution> {
            this.user.id == userId && level == 1
        })
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

    test("insufficient exp does not consume the one-time timing session") {
        val evolution = evo(level = 1, exp = 100L)
        whenever(userEvolutionRepository.findByUserIdForUpdate(userId)).thenReturn(evolution)

        shouldThrow<InsufficientEvolutionExpException> {
            service.attempt(userId, "key-t-insufficient", TimingAttemptCommand("sess-1", 900L))
        }

        // 차감이 먼저 실패해 롤백되므로 인메모리 세션은 보존돼야 한다(이중 손실 방지).
        verify(timingSessionStore, never()).consume(any(), any(), any())
    }

    test("getState exposes current evolution exp") {
        val evolution = UserEvolution(user = user(), level = 1).apply { addExp(750L) }
        whenever(userEvolutionRepository.findByUserId(userId)).thenReturn(evolution)

        val state = service.getState(userId)

        state.currentExp shouldBe 750L
    }

    test("getAttempts with limit 0 throws IllegalArgumentException") {
        shouldThrow<IllegalArgumentException> { service.getAttempts(userId, 0) }
    }

    test("getAttempts with limit 101 throws IllegalArgumentException") {
        shouldThrow<IllegalArgumentException> { service.getAttempts(userId, 101) }
    }

    test("getAttempts with limit 1 and limit 100 do not throw") {
        val now = java.time.Instant.parse("2026-06-25T12:34:56Z")
        val a1 = EvolutionAttempt(userId = userId, fromLevel = 1, cost = 500, success = true, resultLevel = 2, idempotencyKey = "k0")
            .apply { createdAt = now }
        whenever(evolutionAttemptRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), any()))
            .thenReturn(listOf(a1))
        shouldNotThrowAny { service.getAttempts(userId, 1) }
        shouldNotThrowAny { service.getAttempts(userId, 100) }
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

    test("timing attempt consumes session, judges, and uses final success rate") {
        val evolution = evo(level = 1, exp = 1000L)
        whenever(userEvolutionRepository.findByUserIdForUpdate(userId)).thenReturn(evolution)
        val started = java.time.Instant.parse("2026-06-26T00:00:00Z")
        whenever(timingSessionStore.consume(eq("sess-1"), eq(userId), any())).thenReturn(
            TimingSession("sess-1", userId, started, started.plusSeconds(120))
        )
        whenever(evolutionTimingJudge.judge(eq(900L), any(), eq(0.7))).thenReturn(
            TimingJudgement(TimingGrade.PERFECT, 0.10, 0.7, 0.8)
        )
        whenever(probabilityRoller.succeeds(0.8)).thenReturn(true)

        val result = service.attempt(userId, "key-t1", TimingAttemptCommand("sess-1", 900L))

        result.success shouldBe true
        result.timingGrade shouldBe TimingGrade.PERFECT
        result.timingBonusRate shouldBe 0.10
        result.baseSuccessRate shouldBe 0.7
        result.finalSuccessRate shouldBe 0.8
        verify(probabilityRoller).succeeds(0.8)
    }

    test("legacy attempt without timing uses base rule rate and null timing fields") {
        val evolution = evo(level = 1, exp = 1000L)
        whenever(userEvolutionRepository.findByUserIdForUpdate(userId)).thenReturn(evolution)
        whenever(probabilityRoller.succeeds(0.7)).thenReturn(true)

        val result = service.attempt(userId, "key-l1", null)

        result.timingGrade shouldBe null
        result.finalSuccessRate shouldBe null
        verify(timingSessionStore, never()).consume(any(), any(), any())
    }

    test("duplicate timing key returns stored judgement without consuming session again") {
        val evolution = evo(level = 1, exp = 1000L)
        whenever(userEvolutionRepository.findByUserIdForUpdate(userId)).thenReturn(evolution)
        whenever(evolutionAttemptRepository.findByUserIdAndIdempotencyKey(userId, "key-t2")).thenReturn(
            EvolutionAttempt(
                userId = userId, fromLevel = 1, cost = 500, success = true, resultLevel = 2, idempotencyKey = "key-t2",
                timingGrade = TimingGrade.GREAT, timingBonusRate = 0.05, baseSuccessRate = 0.7, finalSuccessRate = 0.75,
            )
        )

        val result = service.attempt(userId, "key-t2", TimingAttemptCommand("sess-x", 720L))

        result.timingGrade shouldBe TimingGrade.GREAT
        result.finalSuccessRate shouldBe 0.75
        verify(timingSessionStore, never()).consume(any(), any(), any())
    }
})
