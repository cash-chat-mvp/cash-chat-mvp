package com.wnl.cashchat.api.domain.inventory.persistence

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.inventory.persistence.repository.UserInventoryRepository
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
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.MySQLContainer

@SpringBootTest
@Transactional
class UserInventoryUpsertIntegrationTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var userInventoryRepository: UserInventoryRepository

    init {
        beforeTest {
            userInventoryRepository.deleteAll()
            userRepository.deleteAll()
        }

        test("upsertQty inserts on first call and accumulates on second") {
            val user = userRepository.save(
                User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "inv")
            )

            userInventoryRepository.upsertQty(user.id, "EVO_STONE", 2)
            userInventoryRepository.upsertQty(user.id, "EVO_STONE", 3)

            val rows = userInventoryRepository.findByUserIdOrderByItemCodeAsc(user.id)
            rows.size shouldBe 1
            rows[0].itemCode shouldBe "EVO_STONE"
            rows[0].qty shouldBe 5
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
