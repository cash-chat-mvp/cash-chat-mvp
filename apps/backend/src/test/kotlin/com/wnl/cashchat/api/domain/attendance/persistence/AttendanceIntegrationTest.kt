package com.wnl.cashchat.api.domain.attendance.persistence

import com.wnl.cashchat.api.domain.attendance.exception.AlreadyCheckedInException
import com.wnl.cashchat.api.domain.attendance.persistence.repository.AttendanceLogRepository
import com.wnl.cashchat.api.domain.attendance.service.AttendanceService
import com.wnl.cashchat.api.domain.attendance.service.BonusItem
import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.economy.exception.EnergyCapExceededException
import com.wnl.cashchat.api.domain.economy.persistence.entity.EnergySourceType
import com.wnl.cashchat.api.domain.economy.persistence.repository.EnergyGrantRepository
import com.wnl.cashchat.api.domain.economy.persistence.repository.UserWalletRepository
import com.wnl.cashchat.api.domain.economy.persistence.repository.WalletLedgerRepository
import com.wnl.cashchat.api.domain.economy.service.EnergyService
import com.wnl.cashchat.api.domain.economy.service.WalletService
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
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@SpringBootTest
class AttendanceIntegrationTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var userWalletRepository: UserWalletRepository
    @Autowired lateinit var walletLedgerRepository: WalletLedgerRepository
    @Autowired lateinit var energyGrantRepository: EnergyGrantRepository
    @Autowired lateinit var attendanceLogRepository: AttendanceLogRepository
    @Autowired lateinit var walletService: WalletService
    @Autowired lateinit var energyService: EnergyService
    @Autowired lateinit var attendanceService: AttendanceService

    init {
        beforeTest {
            attendanceLogRepository.deleteAll()
            walletLedgerRepository.deleteAll()
            energyGrantRepository.deleteAll()
            userWalletRepository.deleteAll()
            userRepository.deleteAll()
        }

        test("first check-in credits 4 energy atomically with the log") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "att"))

            val result = attendanceService.checkIn(user.id, LocalDate.of(2026, 5, 30))

            result.streakDayCount shouldBe 1
            result.awardedEnergy shouldBe 4L
            attendanceLogRepository.findByUserIdAndCheckInDateBetweenOrderByCheckInDateAsc(
                user.id, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)
            ).size shouldBe 1
            userWalletRepository.findByUserId(user.id)!!.energyAvailable shouldBe 4L
            walletLedgerRepository.count() shouldBe 1L
        }

        test("check-in rolls back the attendance log when energy accrual fails (cap exceeded)") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "rollback"))
            walletService.ensureInitialized(user)
            val exp = Instant.now().plus(7, ChronoUnit.DAYS)
            energyService.grant(user.id, 49, EnergySourceType.ADMIN, exp, "seed:rollback")

            shouldThrow<EnergyCapExceededException> {
                attendanceService.checkIn(user.id, LocalDate.of(2026, 5, 30))
            }

            attendanceLogRepository.count() shouldBe 0L
            walletLedgerRepository.count() shouldBe 1L
            userWalletRepository.findByUserId(user.id)!!.energyAvailable shouldBe 49L
        }

        test("duplicate same-day check-in is rejected and writes nothing extra") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "dup"))
            attendanceService.checkIn(user.id, LocalDate.of(2026, 5, 30))

            shouldThrow<AlreadyCheckedInException> {
                attendanceService.checkIn(user.id, LocalDate.of(2026, 5, 30))
            }

            attendanceLogRepository.count() shouldBe 1L
            walletLedgerRepository.count() shouldBe 1L
            userWalletRepository.findByUserId(user.id)!!.energyAvailable shouldBe 4L
        }

        test("reaching day 7 via consecutive check-ins awards fixed 4 energy each day plus bonus") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "wk"))

            lateinit var last: com.wnl.cashchat.api.domain.attendance.service.CheckInResult
            for (day in 1..7) {
                last = attendanceService.checkIn(user.id, LocalDate.of(2026, 5, day))
            }

            last.streakDayCount shouldBe 7
            last.awardedEnergy shouldBe 4L
            last.bonusItems shouldBe listOf(BonusItem("EVO_STONE", 1))
            userWalletRepository.findByUserId(user.id)!!.energyAvailable shouldBe 28L
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
