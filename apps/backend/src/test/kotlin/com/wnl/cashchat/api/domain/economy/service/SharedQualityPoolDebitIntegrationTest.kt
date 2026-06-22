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
class SharedQualityPoolDebitIntegrationTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var service: SharedQualityPoolService
    @Autowired lateinit var repository: SharedQualityPoolRepository
    @Autowired lateinit var transactionTemplate: TransactionTemplate

    init {
        beforeTest { repository.deleteAll() }

        test("tryConsumePremium debits when balance >= delta and returns true") {
            transactionTemplate.executeWithoutResult { service.accrue(BigDecimal("10.0000")) }
            val ok = transactionTemplate.execute { service.tryConsumePremium(BigDecimal("3.0000")) }!!
            ok shouldBe true
            repository.findById(1L).orElseThrow().balance.compareTo(BigDecimal("7.0000")) shouldBe 0
        }

        test("tryConsumePremium returns false and does not go negative when balance < delta (I9)") {
            transactionTemplate.executeWithoutResult { service.accrue(BigDecimal("2.0000")) }
            val ok = transactionTemplate.execute { service.tryConsumePremium(BigDecimal("5.0000")) }!!
            ok shouldBe false
            repository.findById(1L).orElseThrow().balance.compareTo(BigDecimal("2.0000")) shouldBe 0
        }

        test("tryConsumePremium with zero delta allows premium without reducing balance") {
            transactionTemplate.executeWithoutResult { service.accrue(BigDecimal("1.0000")) }
            val ok = transactionTemplate.execute { service.tryConsumePremium(BigDecimal.ZERO) }!!
            ok shouldBe true
            repository.findById(1L).orElseThrow().balance.compareTo(BigDecimal("1.0000")) shouldBe 0
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
