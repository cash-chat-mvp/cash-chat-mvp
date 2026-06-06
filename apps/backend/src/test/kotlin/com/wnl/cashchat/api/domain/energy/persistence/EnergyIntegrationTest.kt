package com.wnl.cashchat.api.domain.energy.persistence

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.energy.exception.InsufficientEnergyException
import com.wnl.cashchat.api.domain.energy.persistence.repository.UserEnergyRepository
import com.wnl.cashchat.api.domain.energy.service.EnergyService
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

@SpringBootTest
class EnergyIntegrationTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var userEnergyRepository: UserEnergyRepository
    @Autowired lateinit var energyService: EnergyService

    init {
        beforeTest {
            userEnergyRepository.deleteAll()
            userRepository.deleteAll()
        }

        test("signup init creates energy row with signupBonus(50)") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "energy-init"))
            energyService.ensureInitialized(user)

            val energy = userEnergyRepository.findByUserId(user.id)!!
            energy.energy shouldBe 50
        }

        test("consume decrements energy by 1; consume at 0 throws InsufficientEnergyException") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "energy-consume"))
            energyService.ensureInitialized(user)

            energyService.consume(user.id)
            userEnergyRepository.findByUserId(user.id)!!.energy shouldBe 49

            // drain to 0
            repeat(49) { energyService.consume(user.id) }
            userEnergyRepository.findByUserId(user.id)!!.energy shouldBe 0

            shouldThrow<InsufficientEnergyException> {
                energyService.consume(user.id)
            }
        }

        test("charge is capped at maxEnergy(50) and never exceeds it") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "energy-charge"))
            energyService.ensureInitialized(user)

            // Drain some energy then charge beyond max
            repeat(5) { energyService.consume(user.id) }
            userEnergyRepository.findByUserId(user.id)!!.energy shouldBe 45

            energyService.charge(user.id, 100)
            userEnergyRepository.findByUserId(user.id)!!.energy shouldBe 50
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
