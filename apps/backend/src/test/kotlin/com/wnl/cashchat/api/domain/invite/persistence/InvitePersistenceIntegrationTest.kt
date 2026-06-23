package com.wnl.cashchat.api.domain.invite.persistence

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.invite.persistence.entity.InviteRedemption
import com.wnl.cashchat.api.domain.invite.persistence.entity.InviteRedemptionStatus
import com.wnl.cashchat.api.domain.invite.persistence.repository.InviteCodeRepository
import com.wnl.cashchat.api.domain.invite.persistence.repository.InviteRedemptionRepository
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
import org.testcontainers.containers.MySQLContainer

@SpringBootTest
class InvitePersistenceIntegrationTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var inviteCodeRepository: InviteCodeRepository
    @Autowired lateinit var inviteRedemptionRepository: InviteRedemptionRepository

    private fun newUser(name: String): User =
        userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = name))

    init {
        beforeTest {
            inviteRedemptionRepository.deleteAll()
            inviteCodeRepository.deleteAll()
            userRepository.deleteAll()
        }

        test("insertIfAbsent creates a code row and is idempotent on user_id") {
            val user = newUser("u1")

            inviteCodeRepository.insertIfAbsent(user.id, "ABC23X")
            inviteCodeRepository.insertIfAbsent(user.id, "ZZZ99Y") // 같은 user_id → no-op

            inviteCodeRepository.findByUserId(user.id)!!.code shouldBe "ABC23X"
            inviteCodeRepository.findByCode("ABC23X")!!.userId shouldBe user.id
            inviteCodeRepository.count() shouldBe 1L
        }

        test("invitee_user_id is unique across redemptions") {
            val inviter = newUser("inviter")
            val invitee = newUser("invitee")
            inviteRedemptionRepository.save(
                InviteRedemption(
                    inviteeUserId = invitee.id, inviterUserId = inviter.id, code = "ABC23X",
                    awardedEnergy = 10, awardedCoin = 500, status = InviteRedemptionStatus.GRANTED,
                )
            )

            shouldThrow<DataIntegrityViolationException> {
                inviteRedemptionRepository.saveAndFlush(
                    InviteRedemption(
                        inviteeUserId = invitee.id, inviterUserId = inviter.id, code = "ABC23X",
                        awardedEnergy = 10, awardedCoin = 0, status = InviteRedemptionStatus.GRANTED_INVITER_CAPPED,
                    )
                )
            }
        }

        test("count helpers split total referrals from coin-awarded referrals") {
            val inviter = newUser("inviter")
            val a = newUser("a"); val b = newUser("b")
            inviteRedemptionRepository.save(
                InviteRedemption(a.id, inviter.id, "ABC23X", 10, 500, InviteRedemptionStatus.GRANTED)
            )
            inviteRedemptionRepository.save(
                InviteRedemption(b.id, inviter.id, "ABC23X", 10, 0, InviteRedemptionStatus.GRANTED_INVITER_CAPPED)
            )

            inviteRedemptionRepository.countByInviterUserId(inviter.id) shouldBe 2L
            inviteRedemptionRepository.countByInviterUserIdAndStatus(inviter.id, InviteRedemptionStatus.GRANTED) shouldBe 1L
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
