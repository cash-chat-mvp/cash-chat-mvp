package com.wnl.cashchat.api.domain.economy.service

import com.wnl.cashchat.api.domain.economy.persistence.repository.SharedQualityPoolRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.MySQLContainer
import java.math.BigDecimal

@SpringBootTest
class SharedQualityPoolServiceIntegrationTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var sharedQualityPoolService: SharedQualityPoolService
    @Autowired lateinit var sharedQualityPoolRepository: SharedQualityPoolRepository
    @Autowired lateinit var transactionTemplate: TransactionTemplate

    init {
        beforeTest { sharedQualityPoolRepository.deleteAll() }

        test("accrue twice accumulates balance correctly") {
            transactionTemplate.executeWithoutResult { sharedQualityPoolService.accrue(BigDecimal("0.32")) }
            transactionTemplate.executeWithoutResult { sharedQualityPoolService.accrue(BigDecimal("0.32")) }

            val pool = sharedQualityPoolRepository.findById(1L).orElseThrow()
            pool.balance.compareTo(BigDecimal("0.64")) shouldBe 0
        }

        test("accrue with zero or negative amount is a no-op") {
            transactionTemplate.executeWithoutResult { sharedQualityPoolService.accrue(BigDecimal.ZERO) }
            transactionTemplate.executeWithoutResult { sharedQualityPoolService.accrue(BigDecimal("-1.00")) }

            sharedQualityPoolRepository.count() shouldBe 0L
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
