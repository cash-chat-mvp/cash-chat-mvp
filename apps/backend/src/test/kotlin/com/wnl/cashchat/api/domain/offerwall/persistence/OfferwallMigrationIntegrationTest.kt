package com.wnl.cashchat.api.domain.offerwall.persistence

import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MySQLContainer

@SpringBootTest
class OfferwallMigrationIntegrationTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var jdbcTemplate: JdbcTemplate

    init {
        test("V11 creates offerwall_user_tokens table") {
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM offerwall_user_tokens", Int::class.java) shouldBe 0
        }

        test("V11 creates tnk_offerwall_callbacks table") {
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tnk_offerwall_callbacks", Int::class.java) shouldBe 0
        }
    }

    companion object {
        private val mysql = MySQLContainer("mysql:8.4.0")
            .withDatabaseName("cashchat").withUsername("cashchat").withPassword("cashchat")

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            if (!mysql.isRunning) mysql.start()
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
            registry.add("spring.datasource.driver-class-name", mysql::getDriverClassName)
            registry.add("app.offerwall.tnk.app-key") { "test-app-key" }
        }
    }
}
