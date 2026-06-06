package com.wnl.cashchat.api.domain.quality.service

import com.wnl.cashchat.api.domain.quality.persistence.entity.DailyPremiumUsage
import com.wnl.cashchat.api.domain.quality.persistence.repository.DailyPremiumUsageRepository
import com.wnl.cashchat.api.domain.quality.persistence.repository.SharedQualityPoolRepository
import com.wnl.cashchat.api.domain.quality.properties.QualityProperties
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * 공용 프리미엄 재원 풀 관리 서비스.
 *
 * accrue: 채팅 마진을 풀에 적립한다.
 * tryConsumePremium: 일일 캡 → 풀 게이트 → 사용량 증가 순으로 프리미엄 인출을 시도한다.
 *   반환값 false = 차단됨(캡 초과 또는 풀 부족). 예외 없음.
 * throttleScale: 풀 잔액 / safetyFloor (0.0~1.0). CC-340 에서 요금 조절에 사용한다.
 *
 * 이 서비스는 quality 도메인 내부에만 의존하며 chat/evolution/energy/ledger 에 의존하지 않는다.
 */
@Service
class QualityPoolService(
    private val poolRepo: SharedQualityPoolRepository,
    private val dailyRepo: DailyPremiumUsageRepository,
    private val props: QualityProperties,
) {
    /**
     * 풀에 [amountCentiPt] centi-pt 를 적립한다.
     * @throws IllegalStateException 풀 행이 시드되지 않은 경우
     */
    @Transactional
    fun accrue(amountCentiPt: Long) {
        val pool = poolRepo.findForUpdate()
            ?: throw IllegalStateException("SharedQualityPool not seeded (id=1 row missing)")
        pool.accrue(amountCentiPt)
    }

    /**
     * 유저([userId])의 프리미엄 요청을 게이팅한다.
     *
     * 처리 순서:
     * 1) 일일 캡 체크 — usage.count >= premiumDailyCapPerUser 이면 즉시 false 반환.
     * 2) 풀 게이트 — pool.tryConsume(deltaCentiPt) == false 이면 false 반환.
     * 3) 사용량 +1 — 행이 없으면 INSERT(count=1); uq 경합 시 DataIntegrityViolation 을 잡아 재조회 후 increment.
     *
     * @return true = 인출 성공, false = 거부됨 (음수 진입 없음)
     * @throws IllegalStateException 풀 행이 시드되지 않은 경우
     */
    @Transactional
    fun tryConsumePremium(userId: Long, deltaCentiPt: Long, today: LocalDate): Boolean {
        // 1) 일일 캡 체크
        var usage = dailyRepo.findByUserIdAndUsageDate(userId, today)
        if (usage != null && usage.count >= props.premiumDailyCapPerUser) return false

        // 2) 풀 게이트
        val pool = poolRepo.findForUpdate()
            ?: throw IllegalStateException("SharedQualityPool not seeded (id=1 row missing)")
        if (!pool.tryConsume(deltaCentiPt)) return false

        // 3) 사용량 +1
        if (usage == null) {
            try {
                dailyRepo.saveAndFlush(DailyPremiumUsage(userId = userId, usageDate = today, count = 1))
            } catch (_: DataIntegrityViolationException) {
                // 동시 INSERT 경합 — 재조회 후 increment
                usage = dailyRepo.findByUserIdAndUsageDate(userId, today)
                usage?.increment()
            }
        } else {
            usage.increment()
        }

        return true
    }

    /**
     * 풀 잔액 / safetyFloor (0.0~1.0).
     * 풀 행이 없으면 0.0 을 반환한다.
     */
    @Transactional(readOnly = true)
    fun throttleScale(): Double {
        val pool = poolRepo.findById1() ?: return 0.0
        return minOf(1.0, pool.balanceCentiPt.toDouble() / props.poolSafetyFloorCentiPt)
    }
}
