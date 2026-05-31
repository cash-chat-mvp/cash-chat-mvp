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
import java.util.concurrent.ConcurrentLinkedQueue
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
            val failures = ConcurrentLinkedQueue<Throwable>()
            repeat(threads) { i ->
                nonceRepository.saveAndFlush(AdRewardNonce(nonce = "rn-$i", userId = user.id, expiresAt = now.plusSeconds(600)))
                storeEvent("rt-$i", "rn-$i")
                pool.submit {
                    ready.countDown(); go.await()
                    try { adRewardService.grantFromCallback(callback("rt-$i", "rn-$i"), now) } catch (e: Throwable) { failures.add(e) }
                }
            }
            ready.await(); go.countDown(); pool.shutdown()
            pool.awaitTermination(30, TimeUnit.SECONDS) shouldBe true

            // 한도 초과는 예외가 아니라 REJECTED 처리이므로 어떤 스레드도 예외를 던지지 않아야 한다.
            failures.map { "${it::class.simpleName}: ${it.message}" } shouldBe emptyList()
            quotaRepository.findByUserIdAndKstDate(user.id, LocalDate.ofInstant(now, kst))!!.usedCount shouldBe 10
            userPointRepository.findByUserId(user.id)!!.balance shouldBe baseline + 40L
            pointTransactionRepository.count() shouldBe 1L
        }

        test("concurrent first grants with no pre-existing quota row create exactly one row (no DIV leak)") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "first"))
            userPointService.ensureInitialized(user)
            val baseline = userPointRepository.findByUserId(user.id)!!.balance
            // quota 행을 미리 만들지 않는다 → 동시 첫 적립들이 lockOrCreateQuota 의 생성 경로(REQUIRES_NEW)를 경합한다.
            val threads = 6
            val pool = Executors.newFixedThreadPool(threads)
            val ready = CountDownLatch(threads)
            val go = CountDownLatch(1)
            val failures = ConcurrentLinkedQueue<Throwable>()
            repeat(threads) { i ->
                nonceRepository.saveAndFlush(AdRewardNonce(nonce = "fn-$i", userId = user.id, expiresAt = now.plusSeconds(600)))
                storeEvent("ft-$i", "fn-$i")
                pool.submit {
                    ready.countDown(); go.await()
                    try { adRewardService.grantFromCallback(callback("ft-$i", "fn-$i"), now) } catch (e: Throwable) { failures.add(e) }
                }
            }
            ready.await(); go.countDown(); pool.shutdown()
            pool.awaitTermination(30, TimeUnit.SECONDS) shouldBe true

            // 동시 생성 충돌이 예외 없이 흡수되고(멱등 INSERT), 행은 정확히 하나, 6회 모두 적립(한도 10 미만).
            failures.map { "${it::class.simpleName}: ${it.message}" } shouldBe emptyList()
            quotaRepository.count() shouldBe 1L
            quotaRepository.findByUserIdAndKstDate(user.id, LocalDate.ofInstant(now, kst))!!.usedCount shouldBe 6
            userPointRepository.findByUserId(user.id)!!.balance shouldBe baseline + 240L
            pointTransactionRepository.count() shouldBe 6L
        }

        test("concurrent grants reusing one nonce credit exactly once (no double spending)") {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "reuse"))
            userPointService.ensureInitialized(user)
            val baseline = userPointRepository.findByUserId(user.id)!!.balance
            // 단일 nonce 하나만 발급하고, 서로 다른 transactionId 6건이 동시에 같은 nonce 로 적립을 시도한다.
            nonceRepository.saveAndFlush(AdRewardNonce(nonce = "shared", userId = user.id, expiresAt = now.plusSeconds(600)))
            val threads = 6
            val pool = Executors.newFixedThreadPool(threads)
            val ready = CountDownLatch(threads)
            val go = CountDownLatch(1)
            val failures = ConcurrentLinkedQueue<Throwable>()
            repeat(threads) { i ->
                storeEvent("st-$i", "shared")
                pool.submit {
                    ready.countDown(); go.await()
                    try { adRewardService.grantFromCallback(callback("st-$i", "shared"), now) } catch (e: Throwable) { failures.add(e) }
                }
            }
            ready.await(); go.countDown(); pool.shutdown()
            pool.awaitTermination(30, TimeUnit.SECONDS) shouldBe true

            // nonce 비관적 락으로 직렬화 → 정확히 1회만 적립, 나머지는 예외 없이 REJECTED_INVALID_NONCE.
            failures.map { "${it::class.simpleName}: ${it.message}" } shouldBe emptyList()
            nonceRepository.findById("shared").get().used shouldBe true
            pointTransactionRepository.count() shouldBe 1L
            userPointRepository.findByUserId(user.id)!!.balance shouldBe baseline + 40L
            (0 until threads).count {
                eventRepository.findByTransactionId("st-$it")!!.rewardStatus == RewardStatus.GRANTED
            } shouldBe 1
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
