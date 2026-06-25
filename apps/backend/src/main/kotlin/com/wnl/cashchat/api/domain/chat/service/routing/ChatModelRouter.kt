package com.wnl.cashchat.api.domain.chat.service.routing

import com.wnl.cashchat.api.domain.energy.service.EnergyService
import com.wnl.cashchat.api.domain.evolution.service.EvolutionService
import com.wnl.cashchat.api.domain.quality.service.QualityPoolService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * 채팅 경제 라우팅 서비스 (CC-340).
 *
 * routeAndConsume 은 채팅 LLM 스트림 시작 직전에 1회 호출한다:
 *
 * 1. energyService.reserve(userId)          → 밥 1 예약 (부족 시 InsufficientEnergyException 전파)
 * 2. qualityPoolService.accrue(MEAL_MARGIN) → 공용 풀에 마진 32 centi-pt 적립
 * 3. level = evolutionService.getState(userId).level
 * 4. scale = qualityPoolService.throttleScale()
 * 5. tier = tierSampler.sample(mini*scale, gpt*scale)
 * 6. tier == NANO → return NANO
 *    delta = if (tier==GPT) GPT_DELTA else MINI_DELTA
 *    return if (tryConsumePremium(...)) tier else NANO (인출 실패 → nano 강등)
 *
 * 의존성 방향: chat → evolution/energy/quality. 역방향 의존 없음.
 */
@Service
class ChatModelRouter(
    private val evolutionService: EvolutionService,
    private val energyService: EnergyService,
    private val qualityPoolService: QualityPoolService,
    private val props: RoutingProperties,
    private val tierSampler: TierSampler,
) {
    private val log = LoggerFactory.getLogger(ChatModelRouter::class.java)

    /**
     * 경제 루프를 1회 수행하고 결정된 [ModelTier] 를 반환한다.
     *
     * @throws com.wnl.cashchat.api.domain.energy.exception.InsufficientEnergyException 밥이 부족한 경우
     */
    @Transactional
    fun routeAndConsume(userId: Long, today: LocalDate): ModelTier {
        // 1. 밥 예약 (부족 시 예외 전파). 정상 완료/실패 시 ChatService 가 정산(settle)/환불(refund)한다.
        energyService.reserve(userId)

        // 2. 공용 풀 마진 적립
        qualityPoolService.accrue(props.mealMarginCentiPt)

        // 3. 레벨 조회
        val level = evolutionService.getState(userId).level

        // 레벨에 확률 정보가 없으면 nano 확정 (안전 fallback)
        val levelProb = props.probFor(level) ?: run {
            log.debug("routeAndConsume: no routing probability for level={}, defaulting to NANO", level)
            return ModelTier.NANO
        }

        // 4. throttleScale 적용
        val scale = qualityPoolService.throttleScale()
        val probMini = levelProb.mini * scale
        val probGpt = levelProb.gpt * scale

        // 5. 티어 추첨
        val tier = tierSampler.sample(probMini, probGpt)
        log.debug(
            "routeAndConsume: userId={} level={} scale={} probMini={} probGpt={} tier={}",
            userId, level, scale, probMini, probGpt, tier
        )

        // 6. nano 는 즉시 반환
        if (tier == ModelTier.NANO) return ModelTier.NANO

        // 7. 프리미엄 게이트: 공용 풀에서 인출 시도
        val delta = if (tier == ModelTier.GPT) props.gptDeltaCentiPt else props.miniDeltaCentiPt
        val granted = qualityPoolService.tryConsumePremium(userId, delta, today)
        return if (granted) {
            log.debug("routeAndConsume: premium granted tier={} delta={}", tier, delta)
            tier
        } else {
            log.debug("routeAndConsume: premium denied, downgrading to NANO tier={} delta={}", tier, delta)
            ModelTier.NANO
        }
    }
}
