package com.wnl.cashchat.api.domain.economy.service

import com.wnl.cashchat.api.domain.economy.properties.EconomyProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

data class RoutingDecision(val tier: ModelTier, val modelOverride: String?)

@Service
class ModelRoutingService(
    private val sharedQualityPoolService: SharedQualityPoolService,
    private val economyProperties: EconomyProperties,
) {
    /**
     * 서버 전용 모델 라우팅 결정(클라이언트 선택 불가, §10.1).
     * 긴급중지(premiumRoutingEnabled=false)면 항상 NANO. 그 외엔 풀에서 premiumDelta 조건부 차감 성공 시 PREMIUM,
     * 실패(잔액 부족) 시 NANO 강등(I8). 풀은 음수 불가(I9, tryConsumePremium 보장).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    fun selectAndConsume(): RoutingDecision {
        if (!economyProperties.premiumRoutingEnabled) return decision(ModelTier.NANO)
        return if (sharedQualityPoolService.tryConsumePremium(economyProperties.premiumDeltaPt)) {
            decision(ModelTier.PREMIUM)
        } else {
            decision(ModelTier.NANO)
        }
    }

    private fun decision(tier: ModelTier): RoutingDecision {
        val name = when (tier) {
            ModelTier.NANO -> economyProperties.nanoModelName
            ModelTier.PREMIUM -> economyProperties.premiumModelName
        }
        return RoutingDecision(tier, name.ifBlank { null })
    }
}
