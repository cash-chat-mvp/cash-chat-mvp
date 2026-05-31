package com.wnl.cashchat.api.domain.ad.persistence

import com.wnl.cashchat.api.domain.ad.persistence.entity.GoogleAdSsvEvent
import com.wnl.cashchat.api.domain.ad.persistence.entity.RewardStatus
import com.wnl.cashchat.api.domain.ad.persistence.repository.GoogleAdSsvEventRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import jakarta.persistence.EntityManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MySQLContainer

@SpringBootTest(properties = ["spring.jpa.hibernate.ddl-auto=create-drop"])
class GoogleAdSsvPersistenceIntegrationTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var googleAdSsvEventRepository: GoogleAdSsvEventRepository

    @Autowired
    lateinit var entityManager: EntityManager

    init {
        beforeTest {
            googleAdSsvEventRepository.deleteAll()
        }

        test("google ad ssv event core fields and raw query string are persisted in mysql") {
            val event = googleAdSsvEventRepository.saveAndFlush(
                GoogleAdSsvEvent(
                    transactionId = "txn-123",
                    userId = "user-42",
                    rewardAmount = 10,
                    rewardItem = "cash",
                    adUnit = "rewarded-ad-unit",
                    keyId = 7,
                    rawQueryString = "ad_network=google&transaction_id=txn-123&reward_amount=10"
                )
            )
            val eventId = event.id

            entityManager.clear()

            val persisted = googleAdSsvEventRepository.findByTransactionId("txn-123")

            persisted?.id shouldBe eventId
            persisted?.transactionId shouldBe "txn-123"
            persisted?.userId shouldBe "user-42"
            persisted?.rewardAmount shouldBe 10
            persisted?.rewardItem shouldBe "cash"
            persisted?.adUnit shouldBe "rewarded-ad-unit"
            persisted?.keyId shouldBe 7
            persisted?.rewardStatus shouldBe RewardStatus.VERIFIED
            persisted?.rawQueryString shouldBe "ad_network=google&transaction_id=txn-123&reward_amount=10"
        }

        test("duplicate google ad ssv transaction id is rejected") {
            googleAdSsvEventRepository.saveAndFlush(
                GoogleAdSsvEvent(
                    transactionId = "txn-duplicate",
                    userId = "user-42",
                    rewardAmount = 10,
                    rewardItem = "cash",
                    adUnit = "rewarded-ad-unit",
                    keyId = 7,
                    rawQueryString = "transaction_id=txn-duplicate"
                )
            )

            shouldThrow<DataIntegrityViolationException> {
                googleAdSsvEventRepository.saveAndFlush(
                    GoogleAdSsvEvent(
                        transactionId = "txn-duplicate",
                        userId = "user-43",
                        rewardAmount = 20,
                        rewardItem = "cash",
                        adUnit = "rewarded-ad-unit",
                        keyId = 8,
                        rawQueryString = "transaction_id=txn-duplicate"
                    )
                )
            }
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
            if (!mysql.isRunning) {
                mysql.start()
            }
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
            registry.add("spring.datasource.driver-class-name", mysql::getDriverClassName)
        }
    }
}
