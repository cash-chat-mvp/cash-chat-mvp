package com.wnl.cashchat.api.domain.chat.service

import com.wnl.cashchat.api.domain.chat.exception.RewardAlreadySettledException
import com.wnl.cashchat.api.domain.chat.persistence.entity.ChatRewardSettlement
import com.wnl.cashchat.api.domain.chat.persistence.entity.ChatRewardType
import com.wnl.cashchat.api.domain.chat.persistence.entity.SettlementStatus
import com.wnl.cashchat.api.domain.chat.persistence.repository.ChatRewardSettlementRepository
import com.wnl.cashchat.api.domain.economy.persistence.entity.UserWallet
import com.wnl.cashchat.api.domain.economy.persistence.entity.WalletLedger
import com.wnl.cashchat.api.domain.economy.persistence.entity.WalletTxType
import com.wnl.cashchat.api.domain.economy.persistence.repository.WalletLedgerRepository
import com.wnl.cashchat.api.domain.economy.properties.EconomyProperties
import com.wnl.cashchat.api.domain.economy.service.EnergyService
import com.wnl.cashchat.api.domain.economy.service.SharedQualityPoolService
import com.wnl.cashchat.api.domain.economy.service.WalletService
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class ChatRewardSettlementService(
    private val chatRewardSettlementRepository: ChatRewardSettlementRepository,
    private val energyService: EnergyService,
    private val walletService: WalletService,
    private val sharedQualityPoolService: SharedQualityPoolService,
    private val walletLedgerRepository: WalletLedgerRepository,
    private val economyProperties: EconomyProperties,
) {
    @Transactional
    fun beginReservation(userId: Long, conversationId: Long, messageId: String): Long {
        chatRewardSettlementRepository
            .findByUserIdAndMessageIdAndRewardType(userId, messageId, ChatRewardType.CHAT_REWARD)
            ?.let { throw RewardAlreadySettledException(messageId) }
        val settlement = try {
            chatRewardSettlementRepository.saveAndFlush(
                ChatRewardSettlement(userId = userId, messageId = messageId, conversationId = conversationId),
            )
        } catch (e: DataIntegrityViolationException) {
            throw RewardAlreadySettledException(messageId) // 동시 동일 messageId
        }
        energyService.reserve(userId, "chat:reserve:$messageId") // 부족 시 EnergyInsufficientException → 전체 롤백
        return settlement.id
    }

    @Transactional
    fun settle(userId: Long, settlementId: Long, assistantMessageId: Long): SettlementResult {
        val settlement = chatRewardSettlementRepository.findByIdForUpdate(settlementId)
            ?: error("settlement $settlementId not found")
        val wallet = walletService.getForUpdate(userId)
        if (settlement.status == SettlementStatus.SETTLED) return settlement.toResult(wallet)

        val messageId = settlement.messageId
        wallet.consumeReserved(1)
        wallet.addPendingPt(economyProperties.chatRewardPt)
        wallet.addExp(economyProperties.evolutionExpPerChat)
        sharedQualityPoolService.accrue(economyProperties.sharedPoolMarginPerChat)

        ledger(userId, WalletTxType.ENERGY_CONSUMED, -1, wallet.energyAvailable, messageId, "chat:consume:$messageId")
        ledger(userId, WalletTxType.POINT_PENDING_GRANTED, economyProperties.chatRewardPt, wallet.pendingCashablePt, messageId, "chat:pt:$messageId")
        ledger(userId, WalletTxType.EXP_GRANTED, economyProperties.evolutionExpPerChat, wallet.evolutionExp, messageId, "chat:exp:$messageId")

        settlement.markSettled(
            assistantMessageId = assistantMessageId,
            energyDelta = -1, pendingPtDelta = economyProperties.chatRewardPt, evolutionExpDelta = economyProperties.evolutionExpPerChat,
            settledAt = Instant.now(),
        )
        return settlement.toResult(wallet)
    }

    @Transactional
    fun refund(userId: Long, settlementId: Long, assistantMessageId: Long?) {
        val settlement = chatRewardSettlementRepository.findByIdForUpdate(settlementId)
            ?: error("settlement $settlementId not found")
        if (settlement.status == SettlementStatus.SETTLED || settlement.status == SettlementStatus.REFUNDED) return
        energyService.refund(userId, "chat:refund:${settlement.messageId}")
        settlement.markRefunded(assistantMessageId)
    }

    private fun ledger(userId: Long, type: WalletTxType, delta: Long, balanceAfter: Long, referenceId: String, key: String) {
        walletLedgerRepository.findByIdempotencyKey(key)?.let { return }
        walletLedgerRepository.save(WalletLedger(userId = userId, type = type, delta = delta,
            balanceAfter = balanceAfter, referenceId = referenceId, idempotencyKey = key))
    }

    private fun ChatRewardSettlement.toResult(wallet: UserWallet) =
        SettlementResult(
            messageId = messageId, status = status,
            energyDelta = energyDelta, pendingPtDelta = pendingPtDelta, evolutionExpDelta = evolutionExpDelta,
            energyBalance = wallet.energyAvailable, pendingCashablePt = wallet.pendingCashablePt, evolutionExp = wallet.evolutionExp,
            settledAt = settledAt,
        )
}
