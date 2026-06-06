package com.wnl.cashchat.api.domain.ledger.service

import com.wnl.cashchat.api.domain.energy.service.EnergyService
import com.wnl.cashchat.api.domain.ledger.persistence.entity.LedgerEntry
import com.wnl.cashchat.api.domain.ledger.persistence.entity.RevenueSource
import com.wnl.cashchat.api.domain.ledger.persistence.repository.LedgerEntryRepository
import com.wnl.cashchat.api.domain.ledger.properties.LedgerProperties
import com.wnl.cashchat.api.domain.point.persistence.entity.PointTransactionReason
import com.wnl.cashchat.api.domain.point.service.UserPointService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.math.floor

/**
 * 통합 회계 서비스. 외부 수익(광고 등)을 단일 공식으로 분배하고 감사 원장에 기록한다.
 *
 * 분배 순서(단일 @Transactional):
 *  1. 멱등 키 중복이면 기존 분배 반환 (point/energy 재적립 없음)
 *  2. 수익원별 보상 설정 확인 — 없으면 IllegalStateException
 *  3. risk reserve = floor(R * riskRate)
 *  4. net = R - risk
 *  5. service reserve = floor(net * serviceRate)
 *  6. companyProfit = net - service - cashablePt - energy
 *  7. 이익 가드(I3): companyProfit >= max(minProfitFloor, floor(net * minProfitRate)) — 미달 시 IllegalArgumentException
 *  8. cashablePt 적립 → energy 적립 → LedgerEntry INSERT
 */
@Service
class LedgerService(
    private val ledgerEntryRepository: LedgerEntryRepository,
    private val userPointService: UserPointService,
    private val energyService: EnergyService,
    private val properties: LedgerProperties,
) {
    @Transactional
    fun recordRevenue(
        userId: Long,
        source: RevenueSource,
        grossRevenue: Long,
        idempotencyKey: String,
    ): RevenueDistribution {
        // 멱등: 이미 처리된 키는 기존 분배 그대로 반환
        ledgerEntryRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)?.let { return it.toDistribution() }

        val reward = properties.rewardFor(source)
            ?: throw IllegalStateException("no reward config for $source")

        val risk = floor(grossRevenue * properties.riskRate).toLong()
        val net = grossRevenue - risk
        val service = floor(net * properties.serviceRate).toLong()
        val companyProfit = net - service - reward.cashablePt - reward.energy

        val minProfit = maxOf(properties.minProfitFloor, floor(net * properties.minProfitRate).toLong())
        require(companyProfit >= minProfit) {
            "revenue too small / reward misconfigured: profit=$companyProfit < $minProfit"
        }

        // 유저 적립
        userPointService.recordTransaction(
            userId,
            reward.cashablePt,
            PointTransactionReason.LEDGER_REWARD,
            "ledger:$source:$userId:$idempotencyKey",
        )
        if (reward.energy > 0) {
            energyService.charge(userId, reward.energy)
        }

        val entry = ledgerEntryRepository.save(
            LedgerEntry(
                userId = userId,
                source = source,
                grossRevenue = grossRevenue,
                riskReserve = risk,
                serviceReserve = service,
                companyProfit = companyProfit,
                cashablePtAwarded = reward.cashablePt,
                energyAwarded = reward.energy,
                idempotencyKey = idempotencyKey,
            )
        )
        return entry.toDistribution()
    }
}

private fun LedgerEntry.toDistribution() = RevenueDistribution(
    grossRevenue = grossRevenue,
    riskReserve = riskReserve,
    serviceReserve = serviceReserve,
    companyProfit = companyProfit,
    cashablePt = cashablePtAwarded,
    energy = energyAwarded,
)
