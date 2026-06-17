package com.wnl.cashchat.api.domain.offerwall.service

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.offerwall.persistence.entity.TnkOfferwallStatus
import com.wnl.cashchat.api.domain.offerwall.persistence.repository.OfferwallUserTokenRepository
import com.wnl.cashchat.api.domain.offerwall.persistence.repository.TnkOfferwallCallbackRepository
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
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
class TnkOfferwallServiceIntegrationTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var userPointRepository: UserPointRepository
    @Autowired lateinit var pointTransactionRepository: PointTransactionRepository
    @Autowired lateinit var callbackRepository: TnkOfferwallCallbackRepository
    @Autowired lateinit var tokenService: OfferwallUserTokenService
    @Autowired lateinit var userPointService: UserPointService
    @Autowired lateinit var service: TnkOfferwallService
    @Autowired lateinit var offerwallUserTokenRepository: OfferwallUserTokenRepository

    private val now = Instant.parse("2026-06-17T00:00:00Z")
    private val appKey = "test-app-key"

    private fun md5Hex(input: String): String =
        MessageDigest.getInstance("MD5").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun params(seqId: String, token: String, payPnt: Long, mdChk: String = md5Hex(appKey + token + seqId)) =
        TnkOfferwallCallbackParams(seqId = seqId, payPnt = payPnt, mdUserNm = token, mdChk = mdChk, rawQuery = "seq_id=$seqId")

    private fun newUserWithToken(name: String): Pair<Long, String> {
        val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = name))
        userPointService.ensureInitialized(user)
        return user.id to tokenService.tokenFor(user.id)
    }

    init {
        beforeTest {
            callbackRepository.deleteAll()
            pointTransactionRepository.deleteAll()
            userPointRepository.deleteAll()
            // 토큰은 user FK 를 가지므로 user 삭제 전에 비운다
            offerwallUserTokenRepository.deleteAll()
            userRepository.deleteAll()
        }

        test("valid callback credits floor(payPnt * ratio) coins and records GRANTED") {
            val (userId, token) = newUserWithToken("grant")
            val baseline = userPointRepository.findByUserId(userId)!!.balance

            // ratio=0.5 (아래 DynamicPropertySource) → 1500 * 0.5 = 750
            val status = service.handleCallback(params("s1", token, 1500), now)

            status shouldBe TnkOfferwallStatus.GRANTED
            userPointRepository.findByUserId(userId)!!.balance shouldBe baseline + 750L
            val row = callbackRepository.findBySeqId("s1")!!
            row.status shouldBe TnkOfferwallStatus.GRANTED
            row.userId shouldBe userId
            row.coinAmount shouldBe 750L
            pointTransactionRepository.count() shouldBe 1L
        }

        test("conversion floors fractional results") {
            val (_, token) = newUserWithToken("floor")
            // 1501 * 0.5 = 750.5 → floor 750
            service.handleCallback(params("s2", token, 1501), now)
            callbackRepository.findBySeqId("s2")!!.coinAmount shouldBe 750L
        }

        test("bad signature is rejected without persisting a ledger row and credits nothing") {
            val (userId, token) = newUserWithToken("badsig")
            val baseline = userPointRepository.findByUserId(userId)!!.balance

            val status = service.handleCallback(params("s3", token, 1000, mdChk = "wrong"), now)

            status shouldBe TnkOfferwallStatus.REJECTED_BAD_SIGNATURE
            userPointRepository.findByUserId(userId)!!.balance shouldBe baseline
            // 서명 검증을 DB 쓰기 앞에서 수행하므로 원장 행이 생기지 않는다(미검증 요청 차단).
            callbackRepository.findBySeqId("s3") shouldBe null
            pointTransactionRepository.count() shouldBe 0L
        }

        test("unknown token records REJECTED_UNKNOWN_USER and credits nothing") {
            val status = service.handleCallback(params("s4", "ghost-token", 1000), now)

            status shouldBe TnkOfferwallStatus.REJECTED_UNKNOWN_USER
            val row = callbackRepository.findBySeqId("s4")!!
            row.status shouldBe TnkOfferwallStatus.REJECTED_UNKNOWN_USER
            row.userId shouldBe null
            pointTransactionRepository.count() shouldBe 0L
        }

        test("non-positive pay_pnt is rejected and never deducts points") {
            val (userId, token) = newUserWithToken("nonpos")
            val baseline = userPointRepository.findByUserId(userId)!!.balance

            val status = service.handleCallback(params("s7", token, -1000), now)

            status shouldBe TnkOfferwallStatus.REJECTED_NON_POSITIVE
            userPointRepository.findByUserId(userId)!!.balance shouldBe baseline
            callbackRepository.findBySeqId("s7")!!.status shouldBe TnkOfferwallStatus.REJECTED_NON_POSITIVE
            pointTransactionRepository.count() shouldBe 0L
        }

        test("duplicate seq_id does not double-credit") {
            val (userId, token) = newUserWithToken("dup")
            val baseline = userPointRepository.findByUserId(userId)!!.balance

            service.handleCallback(params("s5", token, 1000), now)
            val second = service.handleCallback(params("s5", token, 1000), now)

            second shouldBe TnkOfferwallStatus.GRANTED // 이미 GRANTED 상태를 멱등 반환
            userPointRepository.findByUserId(userId)!!.balance shouldBe baseline + 500L
            pointTransactionRepository.count() shouldBe 1L
        }

        test("concurrent identical seq_id credits exactly once") {
            val (userId, token) = newUserWithToken("race")
            val baseline = userPointRepository.findByUserId(userId)!!.balance
            val threads = 6
            val pool = Executors.newFixedThreadPool(threads)
            val ready = CountDownLatch(threads)
            val go = CountDownLatch(1)
            val failures = ConcurrentLinkedQueue<Throwable>()
            repeat(threads) {
                pool.submit {
                    ready.countDown(); go.await()
                    try { service.handleCallback(params("s6", token, 1000), now) } catch (e: Throwable) { failures.add(e) }
                }
            }
            ready.await(); go.countDown(); pool.shutdown()
            pool.awaitTermination(30, TimeUnit.SECONDS) shouldBe true

            failures.map { "${it::class.simpleName}: ${it.message}" } shouldBe emptyList()
            userPointRepository.findByUserId(userId)!!.balance shouldBe baseline + 500L
            pointTransactionRepository.count() shouldBe 1L
            callbackRepository.count() shouldBe 1L
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
            registry.add("app.offerwall.tnk.app-key") { "test-app-key" }
            registry.add("app.offerwall.tnk.point-to-coin-ratio") { "0.5" }
        }
    }
}
