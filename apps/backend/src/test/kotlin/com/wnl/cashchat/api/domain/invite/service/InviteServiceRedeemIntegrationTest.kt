package com.wnl.cashchat.api.domain.invite.service

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.energy.persistence.repository.UserEnergyRepository
import com.wnl.cashchat.api.domain.energy.service.EnergyService
import com.wnl.cashchat.api.domain.invite.exception.AlreadyRedeemedException
import com.wnl.cashchat.api.domain.invite.exception.InvalidCodeException
import com.wnl.cashchat.api.domain.invite.exception.NotEligibleException
import com.wnl.cashchat.api.domain.invite.exception.SelfReferralException
import com.wnl.cashchat.api.domain.invite.persistence.entity.InviteRedemptionStatus
import com.wnl.cashchat.api.domain.invite.persistence.repository.InviteCodeRepository
import com.wnl.cashchat.api.domain.invite.persistence.repository.InviteRedemptionRepository
import com.wnl.cashchat.api.domain.invite.properties.InviteProperties
import com.wnl.cashchat.api.domain.point.persistence.repository.PointTransactionRepository
import com.wnl.cashchat.api.domain.point.persistence.repository.UserPointRepository
import com.wnl.cashchat.api.domain.point.service.UserPointService
import com.wnl.cashchat.api.domain.user.persistence.entity.Role
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import com.wnl.cashchat.api.domain.user.persistence.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MySQLContainer
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
class InviteServiceRedeemIntegrationTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var inviteCodeRepository: InviteCodeRepository
    @Autowired lateinit var inviteRedemptionRepository: InviteRedemptionRepository
    @Autowired lateinit var inviteService: InviteService
    @Autowired lateinit var properties: InviteProperties
    @Autowired lateinit var energyService: EnergyService
    @Autowired lateinit var userPointService: UserPointService
    @Autowired lateinit var userEnergyRepository: UserEnergyRepository
    @Autowired lateinit var userPointRepository: UserPointRepository
    @Autowired lateinit var pointTransactionRepository: PointTransactionRepository

    /** 코인·에너지 지갑까지 초기화된 사용자 생성(가입 시 ensureInitialized 와 동치). */
    private fun newUser(name: String): User {
        val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = name))
        energyService.ensureInitialized(user)
        userPointService.ensureInitialized(user)
        return user
    }

    private fun codeOf(userId: Long): String = inviteService.getMyInvite(userId, Instant.now()).myCode

    init {
        beforeTest {
            // FK 순서: 자식 테이블 → 부모 테이블
            pointTransactionRepository.deleteAll()
            inviteRedemptionRepository.deleteAll()
            inviteCodeRepository.deleteAll()
            userPointRepository.deleteAll()
            userEnergyRepository.deleteAll()
            userRepository.deleteAll()
        }

        test("redeem grants invitee energy and inviter coin within cap") {
            val inviter = newUser("inviter")
            val invitee = newUser("invitee")
            val code = codeOf(inviter.id)
            val inviterCoinBefore = userPointService.getBalance(inviter.id)
            val inviteeEnergyBefore = energyService.getEnergy(invitee.id).energy

            val result = inviteService.redeem(invitee.id, code, Instant.now())

            result.awardedEnergy shouldBe properties.inviteeRewardEnergy
            result.status shouldBe InviteRedemptionStatus.GRANTED
            userPointService.getBalance(inviter.id) shouldBe inviterCoinBefore + properties.inviterRewardCoin
            energyService.getEnergy(invitee.id).energy shouldBe
                minOf(inviteeEnergyBefore + properties.inviteeRewardEnergy, maxEnergy())
            inviteRedemptionRepository.existsByInviteeUserId(invitee.id) shouldBe true
        }

        test("redeem rejects the user's own code") {
            val user = newUser("self")
            val code = codeOf(user.id)

            shouldThrow<SelfReferralException> { inviteService.redeem(user.id, code, Instant.now()) }
        }

        test("redeem rejects an unknown code") {
            val invitee = newUser("invitee")

            shouldThrow<InvalidCodeException> { inviteService.redeem(invitee.id, "NOPE99", Instant.now()) }
        }

        test("redeem rejects a second attempt by the same user") {
            val inviter = newUser("inviter")
            val invitee = newUser("invitee")
            val code = codeOf(inviter.id)
            inviteService.redeem(invitee.id, code, Instant.now())

            shouldThrow<AlreadyRedeemedException> { inviteService.redeem(invitee.id, code, Instant.now()) }
        }

        test("redeem rejects an invitee past the signup window") {
            val inviter = newUser("inviter")
            val invitee = newUser("invitee")
            val code = codeOf(inviter.id)
            val pastWindow = Instant.now().plus(Duration.ofDays(properties.redeemWindowDays.toLong() + 1))

            shouldThrow<NotEligibleException> { inviteService.redeem(invitee.id, code, pastWindow) }
        }

        test("concurrent redeems of one code never exceed the inviter cap") {
            // inviter-cap=1 (DynamicPropertySource). 4 distinct invitees redeem A's code at once;
            // exactly one must get GRANTED (+coin once), the rest GRANTED_INVITER_CAPPED.
            val inviter = newUser("inviter")
            val code = codeOf(inviter.id)
            val inviterCoinBefore = userPointService.getBalance(inviter.id)
            val invitees = (1..4).map { newUser("invitee$it") }

            val pool = Executors.newFixedThreadPool(invitees.size)
            val ready = CountDownLatch(invitees.size)
            val go = CountDownLatch(1)
            val statuses = ConcurrentLinkedQueue<InviteRedemptionStatus>()
            val failures = ConcurrentLinkedQueue<Throwable>()
            invitees.forEach { invitee ->
                pool.submit {
                    ready.countDown(); go.await()
                    try { statuses.add(inviteService.redeem(invitee.id, code, Instant.now()).status) }
                    catch (e: Throwable) { failures.add(e) }
                }
            }
            ready.await(); go.countDown(); pool.shutdown()
            pool.awaitTermination(30, TimeUnit.SECONDS) shouldBe true

            failures.map { "${it::class.simpleName}: ${it.message}" } shouldBe emptyList()
            statuses.count { it == InviteRedemptionStatus.GRANTED } shouldBe 1
            statuses.count { it == InviteRedemptionStatus.GRANTED_INVITER_CAPPED } shouldBe 3
            userPointService.getBalance(inviter.id) shouldBe inviterCoinBefore + properties.inviterRewardCoin
            inviteRedemptionRepository.countByInviterUserId(inviter.id) shouldBe 4L
        }

        test("redeem normalizes the code via trim and uppercase") {
            val inviter = newUser("inviter")
            val invitee = newUser("invitee")
            val code = codeOf(inviter.id)

            val result = inviteService.redeem(invitee.id, "  ${code.lowercase()}  ", Instant.now())

            result.status shouldBe InviteRedemptionStatus.GRANTED
            inviteRedemptionRepository.existsByInviteeUserId(invitee.id) shouldBe true
        }

        test("over-cap redeem still grants invitee energy but no inviter coin") {
            // inviter-cap 은 DynamicPropertySource 에서 1 로 강제.
            val inviter = newUser("inviter")
            val code = codeOf(inviter.id)
            val firstInvitee = newUser("first")
            inviteService.redeem(firstInvitee.id, code, Instant.now()) // cap(1) 소진

            val secondInvitee = newUser("second")
            val inviterCoinBefore = userPointService.getBalance(inviter.id)
            val secondEnergyBefore = energyService.getEnergy(secondInvitee.id).energy

            val result = inviteService.redeem(secondInvitee.id, code, Instant.now())

            result.status shouldBe InviteRedemptionStatus.GRANTED_INVITER_CAPPED
            result.awardedEnergy shouldBe properties.inviteeRewardEnergy
            userPointService.getBalance(inviter.id) shouldBe inviterCoinBefore // 코인 미증가
            energyService.getEnergy(secondInvitee.id).energy shouldBe
                minOf(secondEnergyBefore + properties.inviteeRewardEnergy, maxEnergy())
        }
    }

    private fun maxEnergy(): Int = Int.MAX_VALUE // 캡 영향 없도록: 실제 maxEnergy 미만 적립이면 합산값 그대로

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
            registry.add("app.invite.inviter-cap") { "1" }
            registry.add("app.invite.invitee-reward-energy") { "10" }
            registry.add("app.invite.inviter-reward-coin") { "500" }
            // signup-bonus(50) + invitee-reward-energy(10) = 60 < 200, so charge never hits cap in tests.
            registry.add("app.energy.max-energy") { "200" }
        }
    }
}
