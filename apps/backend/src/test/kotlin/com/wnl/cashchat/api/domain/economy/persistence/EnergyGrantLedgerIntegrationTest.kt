package com.wnl.cashchat.api.domain.economy.persistence

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.economy.persistence.entity.EnergyGrant
import com.wnl.cashchat.api.domain.economy.persistence.entity.EnergySourceType
import com.wnl.cashchat.api.domain.economy.persistence.entity.WalletLedger
import com.wnl.cashchat.api.domain.economy.persistence.entity.WalletTxType
import com.wnl.cashchat.api.domain.economy.persistence.repository.EnergyGrantRepository
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
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MySQLContainer
import java.time.Instant
import java.time.temporal.ChronoUnit

@SpringBootTest
class EnergyGrantLedgerIntegrationTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var energyGrantRepository: EnergyGrantRepository
    @Autowired lateinit var walletLedgerRepository: WalletLedgerRepository

    init {
        beforeTest {
            energyGrantRepository.deleteAll()
            walletLedgerRepository.deleteAll()
            userRepository.deleteAll()
        }

        test("findUsableOrderByExpiry returns non-expired positive grants ordered by expiry") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "test"))
            val now = Instant.now()
            energyGrantRepository.save(EnergyGrant(user.id, EnergySourceType.REWARDED_AD, 3, now, now.plus(30, ChronoUnit.DAYS)))
            energyGrantRepository.save(EnergyGrant(user.id, EnergySourceType.ATTENDANCE_AD, 4, now, now.plus(7, ChronoUnit.DAYS)))
            energyGrantRepository.save(EnergyGrant(user.id, EnergySourceType.EVENT, 5, now, now.minus(1, ChronoUnit.DAYS)))
            energyGrantRepository.findUsableOrderByExpiry(user.id, now).map { it.sourceType } shouldBe
                listOf(EnergySourceType.ATTENDANCE_AD, EnergySourceType.REWARDED_AD)
        }

        test("duplicate ledger idempotency key is rejected by unique constraint") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "test2"))
            walletLedgerRepository.saveAndFlush(WalletLedger(user.id, WalletTxType.ENERGY_GRANTED, 3, 3, "ads_1", "admob:reward:tx1"))
            shouldThrow<DataIntegrityViolationException> {
                walletLedgerRepository.saveAndFlush(WalletLedger(user.id, WalletTxType.ENERGY_GRANTED, 3, 6, "ads_2", "admob:reward:tx1"))
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
