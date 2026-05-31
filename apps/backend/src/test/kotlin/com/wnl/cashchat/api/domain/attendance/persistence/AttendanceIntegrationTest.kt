package com.wnl.cashchat.api.domain.attendance.persistence

import com.wnl.cashchat.api.domain.attendance.exception.AlreadyCheckedInException
import com.wnl.cashchat.api.domain.attendance.persistence.repository.AttendanceLogRepository
import com.wnl.cashchat.api.domain.attendance.service.AttendanceService
import com.wnl.cashchat.api.domain.attendance.service.BonusItem
import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MySQLContainer
import java.time.LocalDate

@SpringBootTest
class AttendanceIntegrationTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var userPointRepository: UserPointRepository
    @Autowired lateinit var pointTransactionRepository: PointTransactionRepository
    @Autowired lateinit var attendanceLogRepository: AttendanceLogRepository
    @Autowired lateinit var userPointService: UserPointService
    @Autowired lateinit var attendanceService: AttendanceService

    init {
        beforeTest {
            attendanceLogRepository.deleteAll()
            pointTransactionRepository.deleteAll()
            userPointRepository.deleteAll()
            userRepository.deleteAll()
        }

        test("first check-in credits 20 base coins atomically with the log") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "att"))
            userPointService.ensureInitialized(user)
            // 회원가입 초기 잔액을 캡처해 기준으로 삼는다(초기 잔액 설정값이 바뀌어도 견고).
            val baseline = userPointRepository.findByUserId(user.id)!!.balance

            val result = attendanceService.checkIn(user.id, LocalDate.of(2026, 5, 30))

            result.streakDayCount shouldBe 1
            result.awardedCoin shouldBe 20L
            attendanceLogRepository.findByUserIdAndCheckInDateBetweenOrderByCheckInDateAsc(
                user.id, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)
            ).size shouldBe 1
            userPointRepository.findByUserId(user.id)!!.balance shouldBe baseline + 20L
            pointTransactionRepository.count() shouldBe 1L
        }

        test("check-in rolls back the attendance log when point accrual fails") {
            // ensureInitialized 를 일부러 호출하지 않아 recordTransaction 이 IllegalStateException 으로 실패한다.
            // checkIn 의 단일 @Transactional 덕분에 먼저 saveAndFlush 된 attendance_log 가 함께 롤백돼야 한다.
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "rollback"))

            shouldThrow<IllegalStateException> {
                attendanceService.checkIn(user.id, LocalDate.of(2026, 5, 30))
            }

            attendanceLogRepository.count() shouldBe 0L
            pointTransactionRepository.count() shouldBe 0L
        }

        test("duplicate same-day check-in is rejected and writes nothing extra") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "dup"))
            userPointService.ensureInitialized(user)
            val baseline = userPointRepository.findByUserId(user.id)!!.balance
            attendanceService.checkIn(user.id, LocalDate.of(2026, 5, 30))

            shouldThrow<AlreadyCheckedInException> {
                attendanceService.checkIn(user.id, LocalDate.of(2026, 5, 30))
            }

            attendanceLogRepository.count() shouldBe 1L
            pointTransactionRepository.count() shouldBe 1L
            userPointRepository.findByUserId(user.id)!!.balance shouldBe baseline + 20L
        }

        test("reaching day 7 via consecutive check-ins awards the seeded 50 coins plus bonus") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "wk"))
            userPointService.ensureInitialized(user)
            val baseline = userPointRepository.findByUserId(user.id)!!.balance

            lateinit var last: com.wnl.cashchat.api.domain.attendance.service.CheckInResult
            for (day in 1..7) {
                last = attendanceService.checkIn(user.id, LocalDate.of(2026, 5, day))
            }

            last.streakDayCount shouldBe 7
            last.awardedCoin shouldBe 50L
            last.bonusItems shouldBe listOf(BonusItem("EVO_STONE", 1))
            // 1~6일 = 6 x 20, 7일 = 50 → baseline + 170
            userPointRepository.findByUserId(user.id)!!.balance shouldBe baseline + 170L
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
