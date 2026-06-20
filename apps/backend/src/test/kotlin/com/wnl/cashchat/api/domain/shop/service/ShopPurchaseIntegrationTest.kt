package com.wnl.cashchat.api.domain.shop.service

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.inventory.persistence.repository.UserInventoryRepository
import com.wnl.cashchat.api.domain.inventory.service.InventoryLine
import com.wnl.cashchat.api.domain.point.persistence.entity.PointTransactionReason
import com.wnl.cashchat.api.domain.point.persistence.repository.PointTransactionRepository
import com.wnl.cashchat.api.domain.point.persistence.repository.UserPointRepository
import com.wnl.cashchat.api.domain.point.service.UserPointService
import com.wnl.cashchat.api.domain.shop.exception.IdempotencyKeyConflictException
import com.wnl.cashchat.api.domain.shop.exception.InsufficientCoinException
import com.wnl.cashchat.api.domain.shop.exception.ItemInactiveException
import com.wnl.cashchat.api.domain.shop.exception.ItemNotFoundException
import com.wnl.cashchat.api.domain.shop.persistence.repository.PurchaseOrderRepository
import com.wnl.cashchat.api.domain.user.persistence.entity.Role
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import com.wnl.cashchat.api.domain.user.persistence.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MySQLContainer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
class ShopPurchaseIntegrationTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var userPointRepository: UserPointRepository
    @Autowired lateinit var pointTransactionRepository: PointTransactionRepository
    @Autowired lateinit var userInventoryRepository: UserInventoryRepository
    @Autowired lateinit var purchaseOrderRepository: PurchaseOrderRepository
    @Autowired lateinit var userPointService: UserPointService
    @Autowired lateinit var facade: ShopPurchaseFacade
    @Autowired lateinit var jdbc: JdbcTemplate

    /** user_points 행을 만든 뒤 원하는 시작 잔액으로 맞춘다(초기 시드 잔액과 무관하게). */
    private fun newUserWithBalance(name: String, balance: Long): Long {
        val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = name))
        userPointService.ensureInitialized(user)
        val current = userPointRepository.findByUserId(user.id)!!.balance
        if (balance > current) {
            userPointService.recordTransaction(
                user.id, balance - current, PointTransactionReason.AD_REWARD, "setup:$name"
            )
        }
        return user.id
    }

    init {
        beforeTest {
            purchaseOrderRepository.deleteAll()
            userInventoryRepository.deleteAll()
            pointTransactionRepository.deleteAll()
            userPointRepository.deleteAll()
            userRepository.deleteAll()
        }

        test("normal purchase debits coin and grants inventory atomically") {
            val userId = newUserWithBalance("normal", 1250)

            val result = facade.purchase(userId, PurchaseCommand("EVO_STONE", 1, "k1"))

            result.status shouldBe com.wnl.cashchat.api.domain.shop.persistence.entity.PurchaseOrderStatus.COMPLETED
            result.coinBalance shouldBe 1050L
            result.inventory shouldBe listOf(InventoryLine("EVO_STONE", 1))
            purchaseOrderRepository.findByUserIdAndIdempotencyKey(userId, "k1")!!.snapshotPrice shouldBe 200L
            pointTransactionRepository.findByIdempotencyKey("shop:purchase:$userId:k1")!!.delta shouldBe -200L
        }

        test("package purchase grants multiple items in one transaction") {
            val userId = newUserWithBalance("pack", 2000)

            val result = facade.purchase(userId, PurchaseCommand("ENHANCE_PACK", 1, "k2"))

            result.coinBalance shouldBe 800L
            result.inventory.first { it.itemCode == "EVO_STONE" }.qty shouldBe 5
            result.inventory.first { it.itemCode == "LUCK_CHARM" }.qty shouldBe 1
        }

        test("qty>1 scales both price and grant") {
            val userId = newUserWithBalance("qty", 1000)

            val result = facade.purchase(userId, PurchaseCommand("EVO_STONE", 2, "k-qty"))

            result.coinBalance shouldBe 600L
            result.inventory.first { it.itemCode == "EVO_STONE" }.qty shouldBe 2
        }

        test("insufficient coin throws InsufficientCoinException and changes nothing") {
            val userId = newUserWithBalance("poor", 100)

            shouldThrow<InsufficientCoinException> {
                facade.purchase(userId, PurchaseCommand("EVO_STONE", 1, "k3"))
            }

            userPointRepository.findByUserId(userId)!!.balance shouldBe 100L
            userInventoryRepository.findByUserIdOrderByItemCodeAsc(userId) shouldBe emptyList()
            purchaseOrderRepository.findByUserIdAndIdempotencyKey(userId, "k3") shouldBe null
        }

        test("unknown itemCode throws ItemNotFoundException") {
            val userId = newUserWithBalance("nf", 1000)
            shouldThrow<ItemNotFoundException> {
                facade.purchase(userId, PurchaseCommand("NOPE", 1, "k4"))
            }
        }

        test("qty < 1 is rejected by the service invariant guard") {
            val userId = newUserWithBalance("badqty", 1000)
            shouldThrow<IllegalArgumentException> {
                facade.purchase(userId, PurchaseCommand("EVO_STONE", 0, "kq0"))
            }
        }

        test("idempotent replay returns current state without double-debit") {
            val userId = newUserWithBalance("idem", 1250)

            val first = facade.purchase(userId, PurchaseCommand("EVO_STONE", 1, "k5"))
            val second = facade.purchase(userId, PurchaseCommand("EVO_STONE", 1, "k5"))

            second.purchaseOrderId shouldBe first.purchaseOrderId
            userPointRepository.findByUserId(userId)!!.balance shouldBe 1050L
            userInventoryRepository.findByUserIdOrderByItemCodeAsc(userId).first().qty shouldBe 1
            purchaseOrderRepository.count() shouldBe 1L
        }

        test("same key with different payload throws IdempotencyKeyConflictException") {
            val userId = newUserWithBalance("conflict", 2000)
            facade.purchase(userId, PurchaseCommand("EVO_STONE", 1, "k6"))

            shouldThrow<IdempotencyKeyConflictException> {
                facade.purchase(userId, PurchaseCommand("LUCK_CHARM", 1, "k6"))
            }
        }

        test("same key by different users are independent orders") {
            val a = newUserWithBalance("userA", 1000)
            val b = newUserWithBalance("userB", 1000)

            facade.purchase(a, PurchaseCommand("EVO_STONE", 1, "shared"))
            facade.purchase(b, PurchaseCommand("EVO_STONE", 1, "shared"))

            userPointRepository.findByUserId(a)!!.balance shouldBe 800L
            userPointRepository.findByUserId(b)!!.balance shouldBe 800L
            purchaseOrderRepository.count() shouldBe 2L
        }

        test("inactive item throws ItemInactiveException") {
            val userId = newUserWithBalance("inactive", 1000)
            // 시드를 변형하지 않고 전용 비활성 아이템을 삽입(purchase 는 grant 조회 전에 ITEM_INACTIVE 를 던진다)
            jdbc.update(
                "INSERT INTO shop_item (item_code, name, category, price_coin, effect_summary, is_active, display_order) " +
                    "VALUES ('TEST_INACTIVE', '비활성', 'ENHANCE', 100, '테스트', FALSE, 999)"
            )

            shouldThrow<ItemInactiveException> {
                facade.purchase(userId, PurchaseCommand("TEST_INACTIVE", 1, "k7"))
            }

            jdbc.update("DELETE FROM shop_item WHERE item_code = 'TEST_INACTIVE'")
        }

        test("concurrent same-key purchases debit exactly once") {
            val userId = newUserWithBalance("race", 1000)
            val threads = 8
            val pool = Executors.newFixedThreadPool(threads)
            val ready = CountDownLatch(threads)
            val go = CountDownLatch(1)
            val errors = AtomicInteger(0)

            repeat(threads) {
                pool.submit {
                    ready.countDown()
                    go.await()
                    try {
                        facade.purchase(userId, PurchaseCommand("EVO_STONE", 1, "race-key"))
                    } catch (e: Exception) {
                        errors.incrementAndGet()
                    }
                }
            }
            ready.await()
            go.countDown()
            pool.shutdown()
            pool.awaitTermination(30, TimeUnit.SECONDS)

            errors.get() shouldBe 0
            purchaseOrderRepository.count() shouldBe 1L
            userPointRepository.findByUserId(userId)!!.balance shouldBe 800L
            userInventoryRepository.findByUserIdOrderByItemCodeAsc(userId).first().qty shouldBe 1
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
