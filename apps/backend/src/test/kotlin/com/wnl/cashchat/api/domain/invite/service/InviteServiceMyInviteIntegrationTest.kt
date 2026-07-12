package com.wnl.cashchat.api.domain.invite.service

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.invite.persistence.repository.InviteCodeRepository
import com.wnl.cashchat.api.domain.invite.persistence.repository.InviteRedemptionRepository
import com.wnl.cashchat.api.domain.invite.properties.InviteProperties
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
import java.time.Duration
import java.time.Instant

@SpringBootTest
class InviteServiceMyInviteIntegrationTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var inviteCodeRepository: InviteCodeRepository
    @Autowired lateinit var inviteRedemptionRepository: InviteRedemptionRepository
    @Autowired lateinit var inviteService: InviteService
    @Autowired lateinit var properties: InviteProperties

    private fun newUser(name: String): User =
        userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = name))

    init {
        beforeTest {
            inviteRedemptionRepository.deleteAll()
            inviteCodeRepository.deleteAll()
            userRepository.deleteAll()
        }

        test("getMyInvite creates a code on first call and echoes reward config") {
            val user = newUser("u1")

            val view = inviteService.getMyInvite(user.id, Instant.now())

            view.myCode.length shouldBe properties.codeLength
            view.invitedCount shouldBe 0L
            view.redeemAvailable shouldBe true
            view.rewardCoin shouldBe properties.inviterRewardCoin
            view.rewardEnergy shouldBe properties.inviteeRewardEnergy
            inviteCodeRepository.count() shouldBe 1L
        }

        test("getMyInvite returns the same code on repeated calls") {
            val user = newUser("u2")

            val first = inviteService.getMyInvite(user.id, Instant.now()).myCode
            val second = inviteService.getMyInvite(user.id, Instant.now()).myCode

            second shouldBe first
            inviteCodeRepository.count() shouldBe 1L
        }

        test("redeemAvailable is false once the signup window has passed") {
            val user = newUser("u3")
            val pastWindow = Instant.now().plus(Duration.ofDays(properties.redeemWindowDays.toLong() + 1))

            inviteService.getMyInvite(user.id, pastWindow).redeemAvailable shouldBe false
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
