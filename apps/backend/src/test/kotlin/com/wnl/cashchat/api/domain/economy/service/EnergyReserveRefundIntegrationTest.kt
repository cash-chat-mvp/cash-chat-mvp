package com.wnl.cashchat.api.domain.economy.service

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.economy.exception.EnergyInsufficientException
import com.wnl.cashchat.api.domain.economy.persistence.entity.EnergySourceType
import com.wnl.cashchat.api.domain.economy.persistence.entity.WalletTxType
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
class EnergyReserveRefundIntegrationTest : FunSpec() {
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

        test("reserve success: energyAvailable decreases, energyReserved increases, ledger ENERGY_RESERVED written") {
            val userId = newUser()
            energyService.grant(userId, 5, EnergySourceType.REWARDED_AD, exp, "seed")
            energyService.reserve(userId, "chat:reserve:m1")
            val wallet = userWalletRepository.findByUserId(userId)!!
            wallet.energyAvailable shouldBe 4L
            wallet.energyReserved shouldBe 1L
            walletLedgerRepository.findAll()
                .count { it.type == WalletTxType.ENERGY_RESERVED } shouldBe 1
        }

        test("reserve insufficient: fresh user with no grant throws EnergyInsufficientException") {
            val userId = newUser()
            shouldThrow<EnergyInsufficientException> {
                energyService.reserve(userId, "chat:reserve:x")
            }
        }

        test("reserve idempotent: same key twice results in only one reservation") {
            val userId = newUser()
            energyService.grant(userId, 5, EnergySourceType.REWARDED_AD, exp, "seed")
            energyService.reserve(userId, "chat:reserve:m1")
            energyService.reserve(userId, "chat:reserve:m1")
            val wallet = userWalletRepository.findByUserId(userId)!!
            wallet.energyReserved shouldBe 1L
            walletLedgerRepository.findAll()
                .count { it.type == WalletTxType.ENERGY_RESERVED } shouldBe 1
        }

        test("refund: after reserve, refund restores energyAvailable and writes ENERGY_REFUNDED ledger") {
            val userId = newUser()
            energyService.grant(userId, 5, EnergySourceType.REWARDED_AD, exp, "seed")
            energyService.reserve(userId, "chat:reserve:m1")
            energyService.refund(userId, "chat:refund:m1")
            val wallet = userWalletRepository.findByUserId(userId)!!
            wallet.energyAvailable shouldBe 5L
            wallet.energyReserved shouldBe 0L
            walletLedgerRepository.findAll()
                .count { it.type == WalletTxType.ENERGY_REFUNDED } shouldBe 1
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
