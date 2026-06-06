package com.wnl.cashchat.api.domain.evolution.persistence

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.evolution.exception.AlreadyMaxLevelException
import com.wnl.cashchat.api.domain.evolution.persistence.entity.UserEvolution
import com.wnl.cashchat.api.domain.evolution.persistence.repository.EvolutionAttemptRepository
import com.wnl.cashchat.api.domain.evolution.persistence.repository.UserEvolutionRepository
import com.wnl.cashchat.api.domain.evolution.service.EvolutionService
import com.wnl.cashchat.api.domain.evolution.service.ProbabilityRoller
import com.wnl.cashchat.api.domain.point.exception.InsufficientPointsException
import com.wnl.cashchat.api.domain.point.persistence.entity.PointTransactionReason
import com.wnl.cashchat.api.domain.point.persistence.repository.PointTransactionRepository
import com.wnl.cashchat.api.domain.point.persistence.repository.UserPointRepository
import com.wnl.cashchat.api.domain.point.service.UserPointService
import com.wnl.cashchat.api.domain.user.persistence.entity.Role
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import com.wnl.cashchat.api.domain.user.persistence.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.testcontainers.containers.MySQLContainer

@SpringBootTest
class EvolutionIntegrationTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var userPointRepository: UserPointRepository
    @Autowired lateinit var pointTransactionRepository: PointTransactionRepository
    @Autowired lateinit var userEvolutionRepository: UserEvolutionRepository
    @Autowired lateinit var evolutionAttemptRepository: EvolutionAttemptRepository
    @Autowired lateinit var userPointService: UserPointService
    @Autowired lateinit var evolutionService: EvolutionService

    // Replace the real SecureRandomProbabilityRoller with a controllable mock.
    @MockitoBean
    lateinit var probabilityRoller: ProbabilityRoller

    init {
        beforeTest {
            evolutionAttemptRepository.deleteAll()
            pointTransactionRepository.deleteAll()
            userEvolutionRepository.deleteAll()
            userPointRepository.deleteAll()
            userRepository.deleteAll()
        }

        test("successful attempt increments level +1, writes one EVOLUTION_ATTEMPT tx and one evolution_attempt row") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "evo-success"))
            userPointService.ensureInitialized(user)
            // Give enough balance for the attempt cost (Lv1→2 costs 500; initial balance is 1 → top up)
            userPointService.recordTransaction(user.id, 600L, PointTransactionReason.ATTENDANCE, "seed:${user.id}")
            val balanceBefore = userPointRepository.findByUserId(user.id)!!.balance

            evolutionService.ensureInitialized(user)
            val levelBefore = userEvolutionRepository.findByUserId(user.id)!!.level

            // Force success
            whenever(probabilityRoller.succeeds(0.7)).thenReturn(true)

            val result = evolutionService.attempt(user.id, "attempt-success-1")

            result.success shouldBe true
            result.fromLevel shouldBe levelBefore
            result.resultLevel shouldBe levelBefore + 1
            result.cost shouldBe 500L

            userEvolutionRepository.findByUserId(user.id)!!.level shouldBe levelBefore + 1
            pointTransactionRepository.findAll()
                .filter { it.reason == PointTransactionReason.EVOLUTION_ATTEMPT }
                .size shouldBe 1
            evolutionAttemptRepository.count() shouldBe 1L
            userPointRepository.findByUserId(user.id)!!.balance shouldBe balanceBefore - 500L
        }

        test("same idempotencyKey called twice — second is no-op, rows stay at 1, same result") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "evo-idem"))
            userPointService.ensureInitialized(user)
            userPointService.recordTransaction(user.id, 600L, PointTransactionReason.ATTENDANCE, "seed:${user.id}")
            val balanceBefore = userPointRepository.findByUserId(user.id)!!.balance

            evolutionService.ensureInitialized(user)

            whenever(probabilityRoller.succeeds(0.7)).thenReturn(true)

            val first = evolutionService.attempt(user.id, "attempt-idem-1")
            val second = evolutionService.attempt(user.id, "attempt-idem-1")

            second.success shouldBe first.success
            second.resultLevel shouldBe first.resultLevel
            second.cost shouldBe first.cost

            // Only one deduction and one attempt row
            pointTransactionRepository.findAll()
                .filter { it.reason == PointTransactionReason.EVOLUTION_ATTEMPT }
                .size shouldBe 1
            evolutionAttemptRepository.count() shouldBe 1L
            // Balance deducted only once
            userPointRepository.findByUserId(user.id)!!.balance shouldBe balanceBefore - 500L
        }

        test("insufficient balance rolls back — no level change, no ledger rows") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "evo-broke"))
            // ensureInitialized gives initial_balance (1 point) — far below 500 cost
            userPointService.ensureInitialized(user)
            evolutionService.ensureInitialized(user)

            val levelBefore = userEvolutionRepository.findByUserId(user.id)!!.level

            shouldThrow<InsufficientPointsException> {
                evolutionService.attempt(user.id, "attempt-broke-1")
            }

            userEvolutionRepository.findByUserId(user.id)!!.level shouldBe levelBefore
            pointTransactionRepository.findAll()
                .filter { it.reason == PointTransactionReason.EVOLUTION_ATTEMPT }
                .size shouldBe 0
            evolutionAttemptRepository.count() shouldBe 0L
        }
    }

    companion object {
        private val mysql = MySQLContainer("mysql:8.4.0")
            .withDatabaseName("cashchat")
            .withUsername("cashchat")
            .withPassword("cashchat")

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            if (!mysql.isRunning) mysql.start()
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
            registry.add("spring.datasource.driver-class-name", mysql::getDriverClassName)
        }
    }
}
