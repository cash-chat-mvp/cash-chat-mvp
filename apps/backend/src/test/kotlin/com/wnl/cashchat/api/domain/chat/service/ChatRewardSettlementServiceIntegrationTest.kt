package com.wnl.cashchat.api.domain.chat.service

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.chat.exception.RewardAlreadySettledException
import com.wnl.cashchat.api.domain.chat.persistence.entity.ChatMessage
import com.wnl.cashchat.api.domain.chat.persistence.entity.Conversation
import com.wnl.cashchat.api.domain.chat.persistence.entity.MessageRole
import com.wnl.cashchat.api.domain.chat.persistence.entity.MessageStatus
import com.wnl.cashchat.api.domain.chat.persistence.entity.SettlementStatus
import com.wnl.cashchat.api.domain.chat.persistence.repository.ChatMessageRepository
import com.wnl.cashchat.api.domain.chat.persistence.repository.ChatRewardSettlementRepository
import com.wnl.cashchat.api.domain.chat.persistence.repository.ConversationRepository
import com.wnl.cashchat.api.domain.economy.exception.EnergyInsufficientException
import com.wnl.cashchat.api.domain.economy.persistence.entity.EnergySourceType
import com.wnl.cashchat.api.domain.economy.persistence.repository.EnergyGrantRepository
import com.wnl.cashchat.api.domain.economy.persistence.repository.SharedQualityPoolRepository
import com.wnl.cashchat.api.domain.economy.persistence.repository.UserWalletRepository
import com.wnl.cashchat.api.domain.economy.persistence.repository.WalletLedgerRepository
import com.wnl.cashchat.api.domain.economy.service.EnergyService
import com.wnl.cashchat.api.domain.economy.service.WalletService
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
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

@SpringBootTest
class ChatRewardSettlementServiceIntegrationTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var userWalletRepository: UserWalletRepository
    @Autowired lateinit var walletLedgerRepository: WalletLedgerRepository
    @Autowired lateinit var energyGrantRepository: EnergyGrantRepository
    @Autowired lateinit var settlementRepository: ChatRewardSettlementRepository
    @Autowired lateinit var conversationRepository: ConversationRepository
    @Autowired lateinit var chatMessageRepository: ChatMessageRepository
    @Autowired lateinit var poolRepository: SharedQualityPoolRepository
    @Autowired lateinit var walletService: WalletService
    @Autowired lateinit var energyService: EnergyService
    @Autowired lateinit var chatRewardSettlementService: ChatRewardSettlementService

    init {
        beforeTest {
            // Delete in FK-safe order: children before parents
            walletLedgerRepository.deleteAll()
            energyGrantRepository.deleteAll()
            settlementRepository.deleteAll()
            chatMessageRepository.deleteAll()
            conversationRepository.deleteAll()
            userWalletRepository.deleteAll()
            userRepository.deleteAll()
            poolRepository.deleteAll()
        }

        val exp = Instant.now().plus(30, ChronoUnit.DAYS)

        fun setup(): Triple<Long, Long, Long> {
            val user = userRepository.save(User(role = Role.MEMBER, provider = AuthProviderType.NONE, name = "tester"))
            walletService.ensureInitialized(user)
            val conv = conversationRepository.save(Conversation(user = user))
            val assistantMsg = chatMessageRepository.save(
                ChatMessage(conversation = conv, role = MessageRole.ASSISTANT, content = "hi", status = MessageStatus.COMPLETED)
            )
            return Triple(user.id, conv.id, assistantMsg.id)
        }

        test("normal: grant 5 → beginReservation → reserved 1/available 4 → settle → wallet correct, pool 0.32, 4 ledger rows") {
            val (userId, convId, assistantMsgId) = setup()
            energyService.grant(userId, 5, EnergySourceType.REWARDED_AD, exp, "seed:$userId")

            val settlementId = chatRewardSettlementService.beginReservation(userId, convId, "m1")
            val walletAfterReserve = userWalletRepository.findByUserId(userId)!!
            walletAfterReserve.energyAvailable shouldBe 4L
            walletAfterReserve.energyReserved shouldBe 1L

            val result = chatRewardSettlementService.settle(userId, settlementId, assistantMsgId)
            val walletAfterSettle = userWalletRepository.findByUserId(userId)!!
            walletAfterSettle.energyAvailable shouldBe 4L
            walletAfterSettle.energyReserved shouldBe 0L
            walletAfterSettle.pendingCashablePt shouldBe 1L
            walletAfterSettle.evolutionExp shouldBe 1L

            result.status shouldBe SettlementStatus.SETTLED
            settlementRepository.findById(settlementId).get().status shouldBe SettlementStatus.SETTLED

            val pool = poolRepository.findById(1L).get()
            pool.balance.compareTo(BigDecimal("0.32")) shouldBe 0

            // ledger rows: ENERGY_GRANTED(seed) + ENERGY_RESERVED + ENERGY_CONSUMED + POINT_PENDING_GRANTED + EXP_GRANTED = 5
            // but "4 total" in brief means settle's 3 + reserve's 1 = 4 settlement-related rows
            // The brief says "원장: RESERVED+CONSUMED+PENDING+EXP(4건)" meaning 4 rows total from begin+settle
            walletLedgerRepository.count() shouldBe 5L // 1 grant + 1 reserve + 3 settle
        }

        test("idempotent settle: settle twice → pendingCashablePt 1, pool still 0.32") {
            val (userId, convId, assistantMsgId) = setup()
            energyService.grant(userId, 5, EnergySourceType.REWARDED_AD, exp, "seed:$userId")

            val settlementId = chatRewardSettlementService.beginReservation(userId, convId, "m2")
            chatRewardSettlementService.settle(userId, settlementId, assistantMsgId)
            chatRewardSettlementService.settle(userId, settlementId, assistantMsgId)

            val wallet = userWalletRepository.findByUserId(userId)!!
            wallet.pendingCashablePt shouldBe 1L

            val pool = poolRepository.findById(1L).get()
            pool.balance.compareTo(BigDecimal("0.32")) shouldBe 0
            // 두 번째 정산이 원장을 중복 기록하지 않는다(멱등 키 가드)
            walletLedgerRepository.count() shouldBe 5L
        }

        test("refund then settle is rejected (state machine): IllegalStateException, no double-spend") {
            val (userId, convId, assistantMsgId) = setup()
            energyService.grant(userId, 5, EnergySourceType.REWARDED_AD, exp, "seed:$userId")

            val settlementId = chatRewardSettlementService.beginReservation(userId, convId, "m6")
            chatRewardSettlementService.refund(userId, settlementId, null)

            shouldThrow<IllegalStateException> {
                chatRewardSettlementService.settle(userId, settlementId, assistantMsgId)
            }

            val wallet = userWalletRepository.findByUserId(userId)!!
            wallet.energyAvailable shouldBe 5L
            wallet.energyReserved shouldBe 0L
            wallet.pendingCashablePt shouldBe 0L
            settlementRepository.findById(settlementId).get().status shouldBe SettlementStatus.REFUNDED
        }

        test("duplicate messageId: after settle, beginReservation same messageId → RewardAlreadySettledException") {
            val (userId, convId, assistantMsgId) = setup()
            energyService.grant(userId, 5, EnergySourceType.REWARDED_AD, exp, "seed:$userId")

            val settlementId = chatRewardSettlementService.beginReservation(userId, convId, "m3")
            chatRewardSettlementService.settle(userId, settlementId, assistantMsgId)

            shouldThrow<RewardAlreadySettledException> {
                chatRewardSettlementService.beginReservation(userId, convId, "m3")
            }
        }

        test("refund: beginReservation then refund → available 5, reserved 0, REFUNDED, pendingCashablePt 0") {
            val (userId, convId, _) = setup()
            energyService.grant(userId, 5, EnergySourceType.REWARDED_AD, exp, "seed:$userId")

            val settlementId = chatRewardSettlementService.beginReservation(userId, convId, "m4")
            chatRewardSettlementService.refund(userId, settlementId, null)

            val wallet = userWalletRepository.findByUserId(userId)!!
            wallet.energyAvailable shouldBe 5L
            wallet.energyReserved shouldBe 0L
            wallet.pendingCashablePt shouldBe 0L

            settlementRepository.findById(settlementId).get().status shouldBe SettlementStatus.REFUNDED
        }

        test("reserve fail: energy 0 → beginReservation → EnergyInsufficientException, settlement row count 0") {
            val (userId, convId, _) = setup()
            // no grant → energyAvailable = 0

            shouldThrow<EnergyInsufficientException> {
                chatRewardSettlementService.beginReservation(userId, convId, "m5")
            }

            settlementRepository.count() shouldBe 0L
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
            registry.add("app.economy.shared-pool-margin-per-chat") { "0.32" }
        }
    }
}
