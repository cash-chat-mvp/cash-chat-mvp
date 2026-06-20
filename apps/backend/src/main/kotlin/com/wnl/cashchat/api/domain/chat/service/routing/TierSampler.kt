package com.wnl.cashchat.api.domain.chat.service.routing

import org.springframework.stereotype.Component
import java.security.SecureRandom

/**
 * 누적 확률로 모델 티어를 추첨하는 인터페이스.
 * 테스트에서는 mock 으로 결정적 제어, 프로덕션에서는 SecureRandom 구현 사용.
 *
 * @param probMini scale 이 적용된 MINI 선택 확률 (0..1)
 * @param probGpt  scale 이 적용된 GPT 선택 확률 (0..1)
 * @return 추첨된 [ModelTier]
 */
interface TierSampler {
    fun sample(probMini: Double, probGpt: Double): ModelTier
}

/**
 * SecureRandom 기반 누적 확률 추첨.
 * [0, probNano) → NANO, [probNano, probNano+probMini) → MINI, 나머지 → GPT.
 * probNano = 1 - probMini - probGpt.
 */
@Component
class SecureRandomTierSampler : TierSampler {
    private val random = SecureRandom()

    override fun sample(probMini: Double, probGpt: Double): ModelTier {
        val probNano = 1.0 - probMini - probGpt
        val roll = random.nextDouble() // [0.0, 1.0)
        return when {
            roll < probNano -> ModelTier.NANO
            roll < probNano + probMini -> ModelTier.MINI
            else -> ModelTier.GPT
        }
    }
}
