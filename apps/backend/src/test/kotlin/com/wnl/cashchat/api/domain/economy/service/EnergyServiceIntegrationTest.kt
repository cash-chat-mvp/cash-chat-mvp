package com.wnl.cashchat.api.domain.economy.service

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.economy.exception.EnergyCapExceededException
import com.wnl.cashchat.api.domain.economy.persistence.entity.EnergySourceType
import com.wnl.cashchat.api.domain.economy.persistence.repository.EnergyGrantRepository
import com.wnl.cashchat.api.domain.economy.persistence.repository.UserWalletRepository
import com.wnl.cashchat.api.domain.economy.persistence.repository.WalletLedgerRepository
import com.wnl.cashchat.api.domain.user.persistence.entity.Role
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import com.wnl.cashchat.api.domain.user.persistence.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MySQLContainer
import java.time.Instant
import java.time.temporal.ChronoUnit

@SpringBootTest
class EnergyServiceIntegrationTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var userWalletRepository: UserWalletRepository
    @Autowired lateinit var energyGrantRepository: EnergyGrantRepository
    @Autowired lateinit var walletLedgerRepository: WalletLedgerRepository
    @Autowired lateinit var walletService: WalletService
    @Autowired lateinit var energyService: EnergyService

    init {
        beforeTest {
            walletLedgerRepository.deleteAll(); energyGrantRepository.deleteAll()
            userWalletRepository.deleteAll(); userRepository.deleteAll()
        }
        fun newUser(): Long {
            val u = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "g"))
            walletService.ensureInitialized(u); return u.id
        }
        val exp = Instant.now().plus(30, ChronoUnit.DAYS)

        test("grant increases energy, writes a grant row and a ledger entry") {
            val userId = newUser()
            energyService.grant(userId, 3, EnergySourceType.REWARDED_AD, exp, "admob:reward:tx1")
            userWalletRepository.findByUserId(userId)!!.energyAvailable shouldBe 3L
            energyGrantRepository.count() shouldBe 1L
            walletLedgerRepository.count() shouldBe 1L
        }
        test("duplicate idempotency key does not double-grant") {
            val userId = newUser()
            energyService.grant(userId, 3, EnergySourceType.REWARDED_AD, exp, "admob:reward:tx1")
            energyService.grant(userId, 3, EnergySourceType.REWARDED_AD, exp, "admob:reward:tx1")
            userWalletRepository.findByUserId(userId)!!.energyAvailable shouldBe 3L
            energyGrantRepository.count() shouldBe 1L
            walletLedgerRepository.count() shouldBe 1L
        }
        test("grant beyond max energy is rejected") {
            val userId = newUser()
            energyService.grant(userId, 49, EnergySourceType.ADMIN, exp, "seed:1")
            shouldThrow<EnergyCapExceededException> {
                energyService.grant(userId, 3, EnergySourceType.REWARDED_AD, exp, "admob:reward:tx2")
            }
        }
        test("grant lazily bootstraps the wallet when it does not exist yet") {
            // ensureInitialized 를 호출하지 않아 user_wallet 행이 없는 상태
            val u = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "boot"))

            energyService.grant(u.id, 3, EnergySourceType.REWARDED_AD, exp, "admob:reward:tx-boot")

            userWalletRepository.findByUserId(u.id)!!.energyAvailable shouldBe 3L
            walletLedgerRepository.count() shouldBe 1L
            energyGrantRepository.count() shouldBe 1L
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
