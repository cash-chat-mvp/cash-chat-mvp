package com.wnl.cashchat.api.domain.quality.persistence

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.quality.persistence.repository.DailyPremiumUsageRepository
import com.wnl.cashchat.api.domain.quality.persistence.repository.SharedQualityPoolRepository
import com.wnl.cashchat.api.domain.quality.service.QualityPoolService
import com.wnl.cashchat.api.domain.user.persistence.entity.Role
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import com.wnl.cashchat.api.domain.user.persistence.repository.UserRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MySQLContainer
import java.time.LocalDate

@SpringBootTest
class QualityIntegrationTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var poolRepository: SharedQualityPoolRepository
    @Autowired lateinit var dailyRepository: DailyPremiumUsageRepository
    @Autowired lateinit var qualityPoolService: QualityPoolService

    init {
        beforeTest {
            dailyRepository.deleteAll()
            userRepository.deleteAll()
        }

        test("accrue increases pool balance") {
            val before = poolRepository.findById1()?.balanceCentiPt ?: 0L

            qualityPoolService.accrue(1000L)

            val after = poolRepository.findById1()!!.balanceCentiPt
            (after - before) shouldBe 1000L
        }

        test("tryConsumePremium returns false when pool balance is insufficient") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "quality-insufficient"))
            val today = LocalDate.now()

            // Use a delta larger than any realistic balance
            val result = qualityPoolService.tryConsumePremium(user.id, Long.MAX_VALUE / 2, today)

            result shouldBe false
        }

        test("tryConsumePremium returns true and decrements pool when sufficient balance") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "quality-consume"))
            val today = LocalDate.now()

            // Accrue enough first
            qualityPoolService.accrue(5000L)
            val balanceBefore = poolRepository.findById1()!!.balanceCentiPt

            val result = qualityPoolService.tryConsumePremium(user.id, 500L, today)

            result shouldBe true
            poolRepository.findById1()!!.balanceCentiPt shouldBe balanceBefore - 500L
            dailyRepository.findByUserIdAndUsageDate(user.id, today)!!.count shouldBe 1
        }

        test("tryConsumePremium returns false when daily cap (50) is exceeded") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "quality-cap"))
            val today = LocalDate.now()

            // Accrue enough balance to last 51 consumes
            qualityPoolService.accrue(100_000L)

            // Consume up to cap (50)
            repeat(50) {
                val ok = qualityPoolService.tryConsumePremium(user.id, 1L, today)
                ok shouldBe true
            }

            val balanceAtCap = poolRepository.findById1()!!.balanceCentiPt

            // The 51st attempt should be rejected by daily cap — pool must not be decremented
            val result = qualityPoolService.tryConsumePremium(user.id, 1L, today)

            result shouldBe false
            poolRepository.findById1()!!.balanceCentiPt shouldBe balanceAtCap
            dailyRepository.findByUserIdAndUsageDate(user.id, today)!!.count shouldBe 50
        }

        test("throttleScale returns value in [0.0, 1.0]") {
            val scale = qualityPoolService.throttleScale()
            (scale >= 0.0) shouldBe true
            (scale <= 1.0) shouldBe true
        }

        test("throttleScale returns 1.0 when balance meets or exceeds safety floor (500000 centi-pt)") {
            val current = poolRepository.findById1()!!.balanceCentiPt
            val needed = maxOf(0L, 500_000L - current)
            if (needed > 0) qualityPoolService.accrue(needed)

            qualityPoolService.throttleScale() shouldBe (1.0 plusOrMinus 1e-9)
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
