package com.wnl.cashchat.api.domain.chat.persistence

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.chat.persistence.entity.ChatRewardSettlement
import com.wnl.cashchat.api.domain.chat.persistence.entity.ChatRewardType
import com.wnl.cashchat.api.domain.chat.persistence.entity.SettlementStatus
import com.wnl.cashchat.api.domain.chat.persistence.repository.ChatRewardSettlementRepository
import com.wnl.cashchat.api.domain.economy.persistence.repository.SharedQualityPoolRepository
import com.wnl.cashchat.api.domain.user.persistence.entity.Role
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import com.wnl.cashchat.api.domain.user.persistence.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.MySQLContainer
import java.math.BigDecimal
import java.time.Instant

@SpringBootTest
class ChatRewardPersistenceIntegrationTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var settlementRepository: ChatRewardSettlementRepository
    @Autowired lateinit var poolRepository: SharedQualityPoolRepository
    @Autowired lateinit var tx: TransactionTemplate

    init {
        beforeTest {
            settlementRepository.deleteAll()
            userRepository.deleteAll()
            poolRepository.deleteAll()
        }

        test("settlement save, findByUserIdAndMessageIdAndRewardType, and state transition persist") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "x"))
            val saved = settlementRepository.saveAndFlush(
                ChatRewardSettlement(userId = user.id, messageId = "msg-1", conversationId = 1L)
            )

            val found = settlementRepository.findByUserIdAndMessageIdAndRewardType(
                user.id, "msg-1", ChatRewardType.CHAT_REWARD
            )!!
            found.id shouldBe saved.id
            found.status shouldBe SettlementStatus.ENERGY_RESERVED

            found.markGenerating()
            settlementRepository.saveAndFlush(found)
            settlementRepository.findById(found.id).get().status shouldBe SettlementStatus.GENERATING

            found.markSettled(42L, -3L, 10L, 5L, Instant.now())
            settlementRepository.saveAndFlush(found)
            val settled = settlementRepository.findById(found.id).get()
            settled.status shouldBe SettlementStatus.SETTLED
            settled.assistantMessageId shouldBe 42L
            settled.energyDelta shouldBe -3L
            settled.pendingPtDelta shouldBe 10L
            settled.evolutionExpDelta shouldBe 5L
        }

        test("duplicate (user_id, message_id, reward_type) throws DataIntegrityViolationException") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "dup"))
            settlementRepository.saveAndFlush(
                ChatRewardSettlement(userId = user.id, messageId = "msg-dup", conversationId = 1L)
            )
            shouldThrow<DataIntegrityViolationException> {
                settlementRepository.saveAndFlush(
                    ChatRewardSettlement(userId = user.id, messageId = "msg-dup", conversationId = 2L)
                )
            }
        }

        test("insertSingletonIfAbsent called twice creates exactly one row") {
            tx.executeWithoutResult {
                poolRepository.insertSingletonIfAbsent()
                poolRepository.insertSingletonIfAbsent()
            }
            poolRepository.count() shouldBe 1L
        }

        test("accrue twice accumulates balance correctly") {
            tx.executeWithoutResult { poolRepository.insertSingletonIfAbsent() }
            tx.executeWithoutResult { poolRepository.accrue(BigDecimal("0.32")) }
            tx.executeWithoutResult { poolRepository.accrue(BigDecimal("0.32")) }
            val pool = poolRepository.findById(1L).get()
            pool.balance.compareTo(BigDecimal("0.64")) shouldBe 0
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
