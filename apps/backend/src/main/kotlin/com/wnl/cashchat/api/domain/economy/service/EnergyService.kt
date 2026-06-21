package com.wnl.cashchat.api.domain.economy.service

import com.wnl.cashchat.api.domain.economy.persistence.entity.EnergyGrant
import com.wnl.cashchat.api.domain.economy.persistence.entity.EnergySourceType
import com.wnl.cashchat.api.domain.economy.persistence.entity.WalletLedger
import com.wnl.cashchat.api.domain.economy.persistence.entity.WalletTxType
import com.wnl.cashchat.api.domain.economy.persistence.repository.EnergyGrantRepository
import com.wnl.cashchat.api.domain.economy.persistence.repository.WalletLedgerRepository
import com.wnl.cashchat.api.domain.economy.properties.EconomyProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class EnergyService(
    private val walletService: WalletService,
    private val energyGrantRepository: EnergyGrantRepository,
    private val walletLedgerRepository: WalletLedgerRepository,
    private val economyProperties: EconomyProperties,
) {
    /**
     * 멱등 Energy 발행. 단일 트랜잭션에서 지갑 행 락 → 멱등 키 조회 → 상한 검사 후 가산 →
     * EnergyGrant·WalletLedger INSERT. 잠금-먼저 순서로 동일 키 동시 호출을 직렬화하고,
     * 원장 unique 가 최종 방어선이다.
     */
    @Transactional
    fun grant(
        userId: Long,
        amount: Long,
        sourceType: EnergySourceType,
        expiresAt: Instant,
        idempotencyKey: String,
    ): WalletLedger {
        val wallet = walletService.ensureForUpdate(userId)
        walletLedgerRepository.findByIdempotencyKey(idempotencyKey)?.let { return it }

        wallet.grantEnergy(amount, economyProperties.maxEnergy)
        val grant = energyGrantRepository.save(
            EnergyGrant(
                userId = userId,
                sourceType = sourceType,
                grantedAmount = amount,
                grantedAt = Instant.now(),
                expiresAt = expiresAt,
            )
        )
        return walletLedgerRepository.save(
            WalletLedger(
                userId = userId,
                type = WalletTxType.ENERGY_GRANTED,
                delta = amount,
                balanceAfter = wallet.energyAvailable,
                referenceId = grant.id.toString(),
                idempotencyKey = idempotencyKey,
            )
        )
    }
}
