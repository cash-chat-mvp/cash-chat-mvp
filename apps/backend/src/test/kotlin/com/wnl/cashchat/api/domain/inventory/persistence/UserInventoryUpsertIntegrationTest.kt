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
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.MySQLContainer

/**
 * user_inventory 네이티브 UPSERT(ON DUPLICATE KEY UPDATE) 통합 테스트.
 * 각 upsert 를 별도 TransactionTemplate 트랜잭션으로 커밋해, 프로덕션(요청마다 별도 트랜잭션)과 동일하게
 * "커밋된 행에 대한 누적"을 검증한다. 클래스 레벨 @Transactional 을 두지 않는 것은 PointIdempotencyIntegrationTest 컨벤션과 일치.
 */
@SpringBootTest
class UserInventoryUpsertIntegrationTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var userInventoryRepository: UserInventoryRepository
    @Autowired lateinit var transactionManager: PlatformTransactionManager

    private fun upsert(userId: Long, itemCode: String, qty: Int) =
        TransactionTemplate(transactionManager).executeWithoutResult {
            userInventoryRepository.upsertQty(userId, itemCode, qty)
        }

    private fun newUser(name: String): Long =
        userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = name)).id

    init {
        beforeTest {
            userInventoryRepository.deleteAll()
            userRepository.deleteAll()
        }

        test("upsertQty inserts then accumulates across separate committed transactions") {
            val userId = newUser("inv")

            upsert(userId, "EVO_STONE", 2)
            upsert(userId, "EVO_STONE", 3)

            val rows = userInventoryRepository.findByUserIdOrderByItemCodeAsc(userId)
            rows.size shouldBe 1
            rows[0].itemCode shouldBe "EVO_STONE"
            rows[0].qty shouldBe 5
        }

        test("different itemCodes for the same user produce separate rows") {
            val userId = newUser("multi")

            upsert(userId, "LUCK_CHARM", 1)
            upsert(userId, "EVO_STONE", 2)

            userInventoryRepository.findByUserIdOrderByItemCodeAsc(userId)
                .map { it.itemCode to it.qty } shouldBe listOf("EVO_STONE" to 2, "LUCK_CHARM" to 1)
        }

        test("same itemCode for different users does not interfere") {
            val a = newUser("userA")
            val b = newUser("userB")

            upsert(a, "EVO_STONE", 2)
            upsert(b, "EVO_STONE", 5)

            userInventoryRepository.findByUserIdOrderByItemCodeAsc(a).single().qty shouldBe 2
            userInventoryRepository.findByUserIdOrderByItemCodeAsc(b).single().qty shouldBe 5
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
