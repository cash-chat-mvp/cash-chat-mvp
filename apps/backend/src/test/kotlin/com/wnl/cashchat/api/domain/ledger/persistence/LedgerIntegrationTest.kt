package com.wnl.cashchat.api.domain.ledger.persistence

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.energy.persistence.repository.UserEnergyRepository
import com.wnl.cashchat.api.domain.energy.service.EnergyService
import com.wnl.cashchat.api.domain.ledger.persistence.entity.RevenueSource
import com.wnl.cashchat.api.domain.ledger.persistence.repository.LedgerEntryRepository
import com.wnl.cashchat.api.domain.ledger.service.LedgerService
import com.wnl.cashchat.api.domain.point.persistence.entity.PointTransactionReason
import com.wnl.cashchat.api.domain.point.persistence.repository.PointTransactionRepository
import com.wnl.cashchat.api.domain.point.persistence.repository.UserPointRepository
import com.wnl.cashchat.api.domain.point.service.UserPointService
import com.wnl.cashchat.api.domain.user.persistence.entity.Role
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import com.wnl.cashchat.api.domain.user.persistence.repository.UserRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MySQLContainer

@SpringBootTest
class LedgerIntegrationTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var userPointRepository: UserPointRepository
    @Autowired lateinit var pointTransactionRepository: PointTransactionRepository
    @Autowired lateinit var userEnergyRepository: UserEnergyRepository
    @Autowired lateinit var ledgerEntryRepository: LedgerEntryRepository
    @Autowired lateinit var userPointService: UserPointService
    @Autowired lateinit var energyService: EnergyService
    @Autowired lateinit var ledgerService: LedgerService

    init {
        beforeTest {
            ledgerEntryRepository.deleteAll()
            pointTransactionRepository.deleteAll()
            userEnergyRepository.deleteAll()
            userPointRepository.deleteAll()
            userRepository.deleteAll()
        }

        test("R=12 AD recordRevenue credits point +4, energy +3 (capped at max), and inserts one ledger_entry row") {
            val user = userRepository.save(
                User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "ledger-happy")
            )
            userPointService.ensureInitialized(user)
            // Drain energy to 0 so we can verify +3 charge
            energyService.ensureInitialized(user)
            repeat(50) { energyService.consume(user.id) }

            val balanceBefore = userPointRepository.findByUserId(user.id)!!.balance
            val energyBefore = userEnergyRepository.findByUserId(user.id)!!.energy
            energyBefore shouldBe 0

            val result = ledgerService.recordRevenue(user.id, RevenueSource.AD, 12L, "ledger-key-1")

            result.grossRevenue shouldBe 12L
            result.riskReserve shouldBe 1L
            result.serviceReserve shouldBe 1L
            result.companyProfit shouldBe 3L
            result.cashablePt shouldBe 4L
            result.energy shouldBe 3

            userPointRepository.findByUserId(user.id)!!.balance shouldBe balanceBefore + 4L
            userEnergyRepository.findByUserId(user.id)!!.energy shouldBe 3

            pointTransactionRepository.findAll()
                .filter { it.reason == PointTransactionReason.LEDGER_REWARD }
                .size shouldBe 1

            ledgerEntryRepository.count() shouldBe 1L
        }

        test("same idempotency key called twice — second is no-op, rows stay at 1, same result") {
            val user = userRepository.save(
                User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "ledger-idem")
            )
            userPointService.ensureInitialized(user)
            energyService.ensureInitialized(user)
            // Drain energy so we can verify +3 charged only once
            repeat(50) { energyService.consume(user.id) }

            val balanceBefore = userPointRepository.findByUserId(user.id)!!.balance

            val first = ledgerService.recordRevenue(user.id, RevenueSource.AD, 12L, "ledger-key-idem")
            val second = ledgerService.recordRevenue(user.id, RevenueSource.AD, 12L, "ledger-key-idem")

            second.grossRevenue shouldBe first.grossRevenue
            second.cashablePt shouldBe first.cashablePt
            second.energy shouldBe first.energy

            // Point and ledger rows created only once
            pointTransactionRepository.findAll()
                .filter { it.reason == PointTransactionReason.LEDGER_REWARD }
                .size shouldBe 1
            ledgerEntryRepository.count() shouldBe 1L
            userPointRepository.findByUserId(user.id)!!.balance shouldBe balanceBefore + 4L
            // Energy charged only once (3), not twice (6)
            userEnergyRepository.findByUserId(user.id)!!.energy shouldBe 3
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
