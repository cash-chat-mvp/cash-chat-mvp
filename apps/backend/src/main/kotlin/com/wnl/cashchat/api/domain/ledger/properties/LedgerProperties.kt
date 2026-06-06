package com.wnl.cashchat.api.domain.ledger.properties

import com.wnl.cashchat.api.domain.ledger.persistence.entity.RevenueSource
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * 수익 분배 정책(app.ledger).
 * riskRate/serviceRate/minProfitRate 는 0–1 사이 비율, minProfitFloor 는 절대 최소 이익(원).
 * rewards 는 수익원별 유저 보상 금액(cashablePt, energy) 목록.
 */
@Validated
@ConfigurationProperties(prefix = "app.ledger")
data class LedgerProperties(
    val riskRate: Double = 0.15,
    val serviceRate: Double = 0.1,
    val minProfitRate: Double = 0.2,
    val minProfitFloor: Long = 2,
    val rewards: List<SourceReward> = emptyList(),
) {
    data class SourceReward(val source: RevenueSource, val cashablePt: Long, val energy: Int)

    fun rewardFor(source: RevenueSource): SourceReward? = rewards.firstOrNull { it.source == source }
}
