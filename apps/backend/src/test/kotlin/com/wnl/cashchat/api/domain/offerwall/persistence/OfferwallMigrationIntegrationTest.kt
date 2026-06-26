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

        test("V12 adds platform column to tnk_offerwall_callbacks") {
            val count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                    "WHERE table_name = 'tnk_offerwall_callbacks' AND column_name = 'platform'",
                Int::class.java,
            )
            count shouldBe 1
        }

        test("V12 replaces seq_id unique with composite (platform, seq_id)") {
            // 단독 seq_id 유니크 인덱스는 사라지고, 복합 유니크 인덱스가 존재해야 한다.
            val composite = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics " +
                    "WHERE table_name = 'tnk_offerwall_callbacks' AND index_name = 'uk_tnk_offerwall_callbacks_platform_seq_id'",
                Int::class.java,
            )
            composite shouldBe 1
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
            registry.add("app.offerwall.tnk.android.app-key") { "test-app-key" }
            registry.add("app.offerwall.tnk.ios.app-key") { "test-app-key" }
        }
    }
}
