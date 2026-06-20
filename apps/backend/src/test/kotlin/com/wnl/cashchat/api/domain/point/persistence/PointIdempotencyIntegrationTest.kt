package com.wnl.cashchat.api.domain.point.persistence

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.point.persistence.entity.PointTransactionReason
import com.wnl.cashchat.api.domain.point.persistence.repository.PointTransactionRepository
import com.wnl.cashchat.api.domain.point.persistence.repository.UserPointRepository
import com.wnl.cashchat.api.domain.point.service.UserPointService
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
import org.testcontainers.containers.MySQLContainer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
class PointIdempotencyIntegrationTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var userPointRepository: UserPointRepository
    @Autowired lateinit var pointTransactionRepository: PointTransactionRepository
    @Autowired lateinit var userPointService: UserPointService

    init {
        beforeTest {
            pointTransactionRepository.deleteAll()
            userPointRepository.deleteAll()
            userRepository.deleteAll()
        }

        test("duplicate idempotency key does not double-credit") {
            val user = userRepository.save(
                User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "dup")
            )
            userPointService.ensureInitialized(user)

            val first = userPointService.recordTransaction(
                user.id, 50L, PointTransactionReason.ATTENDANCE, "attendance:${user.id}:2026-05-30"
            )
            val second = userPointService.recordTransaction(
                user.id, 50L, PointTransactionReason.ATTENDANCE, "attendance:${user.id}:2026-05-30"
            )

            second.id shouldBe first.id
            pointTransactionRepository.count() shouldBe 1L
            userPointRepository.findByUserId(user.id)!!.balance shouldBe 51L // initial 1 + 50
        }

        test("concurrent same-key calls credit exactly once") {
            val user = userRepository.save(
                User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "race")
            )
            userPointService.ensureInitialized(user)

            val threads = 8
            val pool = Executors.newFixedThreadPool(threads)
            val ready = CountDownLatch(threads)
            val go = CountDownLatch(1)
            val failures = AtomicInteger(0)
            val key = "admob:reward:nonce-xyz"

            repeat(threads) {
                pool.submit {
                    ready.countDown()
                    go.await()
                    try {
                        userPointService.recordTransaction(
                            user.id, 40L, PointTransactionReason.AD_REWARD, key
                        )
                    } catch (e: Exception) {
                        failures.incrementAndGet()
                    }
                }
            }
            ready.await()
            go.countDown()
            pool.shutdown()
            pool.awaitTermination(30, TimeUnit.SECONDS)

            pointTransactionRepository.count() shouldBe 1L
            userPointRepository.findByUserId(user.id)!!.balance shouldBe 41L // initial 1 + 40 once
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
