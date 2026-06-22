package com.wnl.cashchat.api.domain.evolution

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.economy.exception.FeatureDisabledException
import com.wnl.cashchat.api.domain.economy.persistence.repository.UserWalletRepository
import com.wnl.cashchat.api.domain.economy.persistence.repository.WalletLedgerRepository
import com.wnl.cashchat.api.domain.economy.service.WalletService
import com.wnl.cashchat.api.domain.evolution.exception.EvolutionAttemptNotFoundException
import com.wnl.cashchat.api.domain.evolution.exception.EvolutionInsufficientExpException
import com.wnl.cashchat.api.domain.evolution.exception.EvolutionLevelMismatchException
import com.wnl.cashchat.api.domain.evolution.exception.EvolutionMaxLevelException
import com.wnl.cashchat.api.domain.evolution.persistence.entity.EvolutionResult
import com.wnl.cashchat.api.domain.evolution.persistence.repository.EvolutionAttemptRepository
import com.wnl.cashchat.api.domain.evolution.service.EvolutionRandom
import com.wnl.cashchat.api.domain.evolution.service.EvolutionService
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
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.MySQLContainer

@SpringBootTest
class EvolutionServiceTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var service: EvolutionService
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var userWalletRepository: UserWalletRepository
    @Autowired lateinit var walletLedgerRepository: WalletLedgerRepository
    @Autowired lateinit var evolutionAttemptRepository: EvolutionAttemptRepository
    @Autowired lateinit var walletService: WalletService
    @Autowired lateinit var transactionTemplate: TransactionTemplate

    // MockBean replaces the real SecureRandomEvolutionRandom so we can control roll()
    @MockBean lateinit var evolutionRandom: EvolutionRandom

    init {
        beforeTest {
            // FK-safe delete order: children before parents
            walletLedgerRepository.deleteAll()
            evolutionAttemptRepository.deleteAll()
            userWalletRepository.deleteAll()
            userRepository.deleteAll()
        }

        /**
         * Seeds a wallet with the given level, exp, and failStack.
         * The entity setters are private, so we use the public mutators inside a transaction.
         *
         * To reach level=N: call applyEvolutionSuccess() (N-1) times.
         * To reach failStack=F: call applyEvolutionFailure(0.0) F times.
         * To reach exp=E: call addExp(E).
         */
        fun seedWallet(userId: Long, level: Int, exp: Long, failStack: Int) {
            transactionTemplate.execute {
                val wallet = walletService.ensureForUpdate(userId)
                // Advance level from 1 to target by applying success (N-1) times
                repeat(level - 1) { wallet.applyEvolutionSuccess() }
                // Apply failures to build failStack (keepRatio=0.0 to discard any exp accumulated by successes)
                repeat(failStack) { wallet.applyEvolutionFailure(0.0) }
                // Set the desired exp
                wallet.addExp(exp)
            }
        }

        fun newUser(name: String = "tester"): Long {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = name))
            return user.id
        }

        test("success at Lv1 when roll below rate raises level and resets exp/failStack") {
            val userId = newUser()
            seedWallet(userId, level = 1, exp = 30L, failStack = 0)
            whenever(evolutionRandom.roll()).thenReturn(0.5) // 0.5 < 0.80 → SUCCESS

            val r = service.attempt(userId, "k-succ", expectedLevel = 1)

            r.result shouldBe EvolutionResult.SUCCESS
            r.levelAfter shouldBe 2
            r.expAfter shouldBe 0
            r.failStackAfter shouldBe 0
        }

        test("failure at Lv1 keeps level, exp resets (keep 0.0), failStack increments") {
            val userId = newUser()
            seedWallet(userId, level = 1, exp = 40L, failStack = 0)
            whenever(evolutionRandom.roll()).thenReturn(0.99) // 0.99 >= 0.80 → FAIL

            val r = service.attempt(userId, "k-fail", expectedLevel = 1)

            r.result shouldBe EvolutionResult.FAIL
            r.levelAfter shouldBe 1
            r.expAfter shouldBe 0   // keepRatio=0.0 → floor(40*0.0)=0
            r.failStackAfter shouldBe 1
        }

        test("I14: same attemptKey returns stored result without re-rolling") {
            val userId = newUser()
            seedWallet(userId, level = 1, exp = 30L, failStack = 0)
            whenever(evolutionRandom.roll()).thenReturn(0.5) // first call → SUCCESS

            val first = service.attempt(userId, "k-idem", 1)

            // Switch to FAIL roll — but same key must return stored SUCCESS
            whenever(evolutionRandom.roll()).thenReturn(0.99)
            val second = service.attempt(userId, "k-idem", expectedLevel = 1)

            second.id shouldBe first.id
            second.result shouldBe EvolutionResult.SUCCESS

            // Wallet mutated only once: level=2, exp=0
            val wallet = userWalletRepository.findByUserId(userId)!!
            wallet.evolutionLevel shouldBe 2
            wallet.evolutionExp shouldBe 0
        }

        test("failStack raises finalSuccessRate linearly (bonus 0.10)") {
            // level=3, failStack=2, exp=300. base 0.35, final = 0.35 + 2*0.10 = 0.55.
            // roll=0.50 < 0.55 → SUCCESS
            val userId = newUser()
            seedWallet(userId, level = 3, exp = 300L, failStack = 2)
            whenever(evolutionRandom.roll()).thenReturn(0.50)

            val r = service.attempt(userId, "k-stack", expectedLevel = 3)

            r.baseSuccessRate shouldBe 0.35
            r.finalSuccessRate shouldBe 0.55
            r.result shouldBe EvolutionResult.SUCCESS
        }

        test("expectedLevel mismatch throws EvolutionLevelMismatchException") {
            val userId = newUser()
            seedWallet(userId, level = 1, exp = 0L, failStack = 0)
            whenever(evolutionRandom.roll()).thenReturn(0.5)

            shouldThrow<EvolutionLevelMismatchException> {
                service.attempt(userId, "k-mm", expectedLevel = 2)
            }
        }

        test("insufficient exp throws EvolutionInsufficientExpException") {
            val userId = newUser()
            seedWallet(userId, level = 1, exp = 10L, failStack = 0) // 10 < required 30
            whenever(evolutionRandom.roll()).thenReturn(0.5)

            shouldThrow<EvolutionInsufficientExpException> {
                service.attempt(userId, "k-exp", expectedLevel = 1)
            }
        }

        test("max level attempt throws EvolutionMaxLevelException") {
            // level=5 → no policy → max level
            val userId = newUser()
            seedWallet(userId, level = 5, exp = 0L, failStack = 0)
            whenever(evolutionRandom.roll()).thenReturn(0.5)

            shouldThrow<EvolutionMaxLevelException> {
                service.attempt(userId, "k-max", expectedLevel = 5)
            }
        }

        test("findAttempt rejects other user's attempt") {
            val userId = newUser("owner")
            val otherId = newUser("other")
            seedWallet(userId, level = 1, exp = 30L, failStack = 0)
            seedWallet(otherId, level = 1, exp = 0L, failStack = 0)
            whenever(evolutionRandom.roll()).thenReturn(0.5)

            val attempt = service.attempt(userId, "k-own", 1)

            shouldThrow<EvolutionAttemptNotFoundException> {
                service.findAttempt(otherId, attempt.id)
            }
        }
    }

    companion object {
        private val mysql = MySQLContainer("mysql:8.4.0")
            .withDatabaseName("cashchat").withUsername("cashchat").withPassword("cashchat")

        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            if (!mysql.isRunning) mysql.start()
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
            registry.add("spring.datasource.driver-class-name", mysql::getDriverClassName)
        }
    }
}

// Separate spec for FeatureDisabled scenario (needs class-level property override)
@SpringBootTest
@TestPropertySource(properties = ["app.economy.evolution-enabled=false"])
class EvolutionServiceFeatureDisabledTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var service: EvolutionService
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var userWalletRepository: UserWalletRepository
    @Autowired lateinit var evolutionAttemptRepository: EvolutionAttemptRepository
    @Autowired lateinit var walletLedgerRepository: WalletLedgerRepository

    @MockBean lateinit var evolutionRandom: EvolutionRandom

    init {
        beforeTest {
            walletLedgerRepository.deleteAll()
            evolutionAttemptRepository.deleteAll()
            userWalletRepository.deleteAll()
            userRepository.deleteAll()
        }

        test("attempt throws FeatureDisabledException when evolution disabled") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "t"))

            shouldThrow<FeatureDisabledException> {
                service.attempt(user.id, "k-off", 1)
            }
        }
    }

    companion object {
        private val mysql = MySQLContainer("mysql:8.4.0")
            .withDatabaseName("cashchat").withUsername("cashchat").withPassword("cashchat")

        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            if (!mysql.isRunning) mysql.start()
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
            registry.add("spring.datasource.driver-class-name", mysql::getDriverClassName)
        }
    }
}
