package com.wnl.cashchat.api.domain.ad.persistence

import com.wnl.cashchat.api.domain.ad.persistence.entity.AdRewardNonce
import com.wnl.cashchat.api.domain.ad.persistence.entity.GoogleAdSsvEvent
import com.wnl.cashchat.api.domain.ad.persistence.entity.RewardStatus
import com.wnl.cashchat.api.domain.ad.persistence.repository.AdRewardDailyQuotaRepository
import com.wnl.cashchat.api.domain.ad.persistence.repository.AdRewardNonceRepository
import com.wnl.cashchat.api.domain.ad.persistence.repository.GoogleAdSsvEventRepository
import com.wnl.cashchat.api.domain.ad.service.AdRewardService
import com.wnl.cashchat.api.domain.ad.service.GoogleAdSsvCallback
import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
class AdRewardIntegrationTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var userPointRepository: UserPointRepository
    @Autowired lateinit var pointTransactionRepository: PointTransactionRepository
    @Autowired lateinit var eventRepository: GoogleAdSsvEventRepository
    @Autowired lateinit var nonceRepository: AdRewardNonceRepository
    @Autowired lateinit var quotaRepository: AdRewardDailyQuotaRepository
    @Autowired lateinit var userPointService: UserPointService
    @Autowired lateinit var adRewardService: AdRewardService

    private val now = Instant.parse("2026-05-31T00:00:00Z")
    private val kst = ZoneId.of("Asia/Seoul")

    private fun callback(txnId: String, nonce: String) = GoogleAdSsvCallback(
        adUnit = "rewarded", rewardAmount = 10, rewardItem = "coin", timestamp = 1L,
        transactionId = txnId, userId = nonce, signature = "sig", keyId = 1L,
        rawQueryString = "raw-$txnId", signedPayload = "raw",
    )

    private fun storeEvent(txnId: String, nonce: String) =
        eventRepository.saveAndFlush(
            GoogleAdSsvEvent(transactionId = txnId, userId = nonce, rewardAmount = 10, rewardItem = "coin", adUnit = "rewarded", keyId = 1L, rawQueryString = "raw-$txnId")
        )

    init {
        beforeTest {
            quotaRepository.deleteAll()
            nonceRepository.deleteAll()
            eventRepository.deleteAll()
            pointTransactionRepository.deleteAll()
            userPointRepository.deleteAll()
            userRepository.deleteAll()
        }

        test("valid nonce grants configured coins and marks event GRANTED") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "ad"))
            userPointService.ensureInitialized(user)
            val baseline = userPointRepository.findByUserId(user.id)!!.balance
            nonceRepository.saveAndFlush(AdRewardNonce(nonce = "n1", userId = user.id, expiresAt = now.plusSeconds(600)))
            storeEvent("t1", "n1")

            adRewardService.grantFromCallback(callback("t1", "n1"), now)

            eventRepository.findByTransactionId("t1")!!.rewardStatus shouldBe RewardStatus.GRANTED
            nonceRepository.findById("n1").get().used shouldBe true
            userPointRepository.findByUserId(user.id)!!.balance shouldBe baseline + 40L
            pointTransactionRepository.count() shouldBe 1L
        }

        test("duplicate transaction id does not double-credit (idempotency key)") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "dup"))
            userPointService.ensureInitialized(user)
            val baseline = userPointRepository.findByUserId(user.id)!!.balance
            nonceRepository.saveAndFlush(AdRewardNonce(nonce = "n2", userId = user.id, expiresAt = now.plusSeconds(600)))
            storeEvent("t2", "n2")

            adRewardService.grantFromCallback(callback("t2", "n2"), now)
            adRewardService.grantFromCallback(callback("t2", "n2"), now)

            pointTransactionRepository.count() shouldBe 1L
            userPointRepository.findByUserId(user.id)!!.balance shouldBe baseline + 40L
        }

        test("concurrent grants for one user at limit-1 grant exactly once more") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "race"))
            userPointService.ensureInitialized(user)
            val baseline = userPointRepository.findByUserId(user.id)!!.balance
            quotaRepository.saveAndFlush(
                com.wnl.cashchat.api.domain.ad.persistence.entity.AdRewardDailyQuota(
                    userId = user.id, kstDate = LocalDate.ofInstant(now, kst), usedCount = 9
                )
            )
            val threads = 6
            val pool = Executors.newFixedThreadPool(threads)
            val ready = CountDownLatch(threads)
            val go = CountDownLatch(1)
            repeat(threads) { i ->
                nonceRepository.saveAndFlush(AdRewardNonce(nonce = "rn-$i", userId = user.id, expiresAt = now.plusSeconds(600)))
                storeEvent("rt-$i", "rn-$i")
                pool.submit {
                    ready.countDown(); go.await()
                    try { adRewardService.grantFromCallback(callback("rt-$i", "rn-$i"), now) } catch (e: Exception) { }
                }
            }
            ready.await(); go.countDown(); pool.shutdown(); pool.awaitTermination(30, TimeUnit.SECONDS)

            quotaRepository.findByUserIdAndKstDate(user.id, LocalDate.ofInstant(now, kst))!!.usedCount shouldBe 10
            userPointRepository.findByUserId(user.id)!!.balance shouldBe baseline + 40L
            pointTransactionRepository.count() shouldBe 1L
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
        }
    }
}
