package com.wnl.cashchat.api.domain.evolution.persistence

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.evolution.exception.InsufficientEvolutionExpException
import com.wnl.cashchat.api.domain.evolution.persistence.entity.UserEvolution
import com.wnl.cashchat.api.domain.evolution.persistence.repository.EvolutionAttemptRepository
import com.wnl.cashchat.api.domain.evolution.persistence.repository.UserEvolutionRepository
import com.wnl.cashchat.api.domain.energy.persistence.repository.UserEnergyRepository
import com.wnl.cashchat.api.domain.energy.service.EnergyService
import com.wnl.cashchat.api.domain.evolution.service.EvolutionService
import com.wnl.cashchat.api.domain.evolution.service.ProbabilityRoller
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
    @Autowired lateinit var userEvolutionRepository: UserEvolutionRepository
    @Autowired lateinit var evolutionAttemptRepository: EvolutionAttemptRepository
    @Autowired lateinit var userEnergyRepository: UserEnergyRepository
    @Autowired lateinit var energyService: EnergyService
    @Autowired lateinit var evolutionService: EvolutionService

    // Replace the real SecureRandomProbabilityRoller with a controllable mock.
    @MockitoBean
    lateinit var probabilityRoller: ProbabilityRoller

    init {
        beforeTest {
            evolutionAttemptRepository.deleteAll()
            userEvolutionRepository.deleteAll()
            userEnergyRepository.deleteAll()
            userRepository.deleteAll()
        }

        test("successful attempt increments level +1, deducts exp, and writes one evolution_attempt row") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "evo-success"))
            energyService.ensureInitialized(user)
            evolutionService.ensureInitialized(user)
            // Seed enough evolution exp for the attempt cost (Lv1→2 costs 500).
            evolutionService.addExp(user.id, 600L)
            val expBefore = userEvolutionRepository.findByUserId(user.id)!!.exp

            val levelBefore = userEvolutionRepository.findByUserId(user.id)!!.level

            // Force success
            whenever(probabilityRoller.succeeds(0.7)).thenReturn(true)

            val result = evolutionService.attempt(user.id, "attempt-success-1")

            result.success shouldBe true
            result.fromLevel shouldBe levelBefore
            result.resultLevel shouldBe levelBefore + 1
            result.cost shouldBe 500L

            userEvolutionRepository.findByUserId(user.id)!!.level shouldBe levelBefore + 1
            evolutionAttemptRepository.count() shouldBe 1L
            userEvolutionRepository.findByUserId(user.id)!!.exp shouldBe expBefore - 500L
        }

        test("same idempotencyKey called twice — second is no-op, rows stay at 1, same result") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "evo-idem"))
            energyService.ensureInitialized(user)
            evolutionService.ensureInitialized(user)
            evolutionService.addExp(user.id, 600L)
            val expBefore = userEvolutionRepository.findByUserId(user.id)!!.exp

            whenever(probabilityRoller.succeeds(0.7)).thenReturn(true)

            val first = evolutionService.attempt(user.id, "attempt-idem-1")
            val second = evolutionService.attempt(user.id, "attempt-idem-1")

            second.success shouldBe first.success
            second.resultLevel shouldBe first.resultLevel
            second.cost shouldBe first.cost

            // Only one attempt row and one deduction
            evolutionAttemptRepository.count() shouldBe 1L
            userEvolutionRepository.findByUserId(user.id)!!.exp shouldBe expBefore - 500L
        }

        test("insufficient exp rolls back — no level change, no ledger rows") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "evo-broke"))
            energyService.ensureInitialized(user)
            // No exp seeded — balance is 0, far below the 500 cost.
            evolutionService.ensureInitialized(user)

            val levelBefore = userEvolutionRepository.findByUserId(user.id)!!.level

            shouldThrow<InsufficientEvolutionExpException> {
                evolutionService.attempt(user.id, "attempt-broke-1")
            }

            userEvolutionRepository.findByUserId(user.id)!!.level shouldBe levelBefore
            userEvolutionRepository.findByUserId(user.id)!!.exp shouldBe 0L
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
