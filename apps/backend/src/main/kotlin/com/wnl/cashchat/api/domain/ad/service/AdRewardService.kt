package com.wnl.cashchat.api.domain.ad.service

import com.wnl.cashchat.api.domain.ad.persistence.entity.AdRewardDailyQuota
import com.wnl.cashchat.api.domain.ad.persistence.entity.RewardStatus
import com.wnl.cashchat.api.domain.ad.persistence.repository.AdRewardDailyQuotaRepository
import com.wnl.cashchat.api.domain.ad.persistence.repository.AdRewardNonceRepository
import com.wnl.cashchat.api.domain.ad.persistence.repository.GoogleAdSsvEventRepository
import com.wnl.cashchat.api.domain.ad.properties.AdRewardProperties
import com.wnl.cashchat.api.domain.energy.service.EnergyService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * SSV 서명 검증 통과·신규 저장된 광고 이벤트에 대해 밥(energy)을 적립한다(개정 모델).
 * 단일 @Transactional 안에서 nonce 해석 → 일일 한도 행 락 → 밥 적립 → 이벤트 상태 갱신을 원자적으로 수행.
 * callback.userId 는 클라이언트가 SSV user_id 필드에 실은 서버 발급 nonce 다(직접 신뢰 금지).
 * 적립 멱등성은 이벤트 상태 가드(VERIFIED→GRANTED, 비관락)가 보장하므로 별도 멱등키가 필요 없다.
 */
@Service
class AdRewardService(
    private val googleAdSsvEventRepository: GoogleAdSsvEventRepository,
    private val adRewardNonceRepository: AdRewardNonceRepository,
    private val adRewardDailyQuotaRepository: AdRewardDailyQuotaRepository,
    private val energyService: EnergyService,
    private val adRewardProperties: AdRewardProperties,
) {
    @Transactional
    fun grantFromCallback(callback: GoogleAdSsvCallback, now: Instant) {
        // 이벤트를 비관적 쓰기 락으로 조회한다. 동일 transactionId 콜백이 동시에 들어와도 적립을 직렬화해,
        // 한쪽이 GRANTED 로 커밋한 뒤 대기하던 쪽이 stale 상태를 REJECTED 로 덮어쓰는 레이스를 막는다.
        val event = googleAdSsvEventRepository.findForUpdateByTransactionId(callback.transactionId) ?: return
        // VERIFIED(적립 미결정) 상태만 적립을 시도한다. GRANTED(적립 완료)·REJECTED_*(거절 종결) 이벤트는
        // AdMob 재전송 시 멱등하게 건너뛴다 — 불필요한 nonce 락을 피하고, 한도 초과로 거절된 콜백이
        // 다음 날 재전송 시 적립되는 부작용도 막는다. 적립 실패로 VERIFIED 로 남은 이벤트는 재시도된다.
        if (event.rewardStatus != RewardStatus.VERIFIED) {
            return
        }

        // 비관적 쓰기 락으로 nonce 를 조회한다. 동일 nonce 동시 요청을 직렬화해, 뒤 트랜잭션이
        // stale 캐시가 아닌 최신 used 상태를 읽도록 보장 → 단일 사용 nonce 의 중복 적립을 차단한다.
        val nonce = adRewardNonceRepository.findForUpdate(callback.userId)
        if (nonce == null || !nonce.isUsable(now)) {
            event.markRejected(RewardStatus.REJECTED_INVALID_NONCE)
            return
        }

        val kstDate = LocalDate.ofInstant(now, KST)
        val quota = lockOrCreateQuota(nonce.userId, kstDate)
        if (quota.usedCount >= adRewardProperties.dailyLimit) {
            // 한도 초과로 거절돼도 유효 nonce 는 한 번의 광고 시청에 소모된 것이므로 사용 완료 처리한다
            // (단일 사용 보장). 거절은 종결 상태라 TTL 내 동일 nonce 재사용을 막는다.
            nonce.markUsed()
            event.markRejected(RewardStatus.REJECTED_OVER_QUOTA)
            return
        }

        quota.increment()
        nonce.markUsed()
        // 광고 보상은 밥(energy)으로 적립한다(개정 모델). 상한(maxEnergy) 초과분은 charge 내부에서 잘린다.
        energyService.charge(nonce.userId, adRewardProperties.energyAmount)
        event.markGranted()
    }

    @Transactional(readOnly = true)
    fun quotaOf(userId: Long, now: Instant): AdRewardQuota {
        val kstDate = LocalDate.ofInstant(now, KST)
        val usedToday = adRewardDailyQuotaRepository.findByUserIdAndKstDate(userId, kstDate)?.usedCount ?: 0
        val resetAtKst = kstDate.plusDays(1).atStartOfDay(KST).toInstant()
        return AdRewardQuota(
            usedToday = usedToday,
            dailyLimit = adRewardProperties.dailyLimit,
            remaining = (adRewardProperties.dailyLimit - usedToday).coerceAtLeast(0),
            resetAtKst = resetAtKst,
        )
    }

    private fun lockOrCreateQuota(userId: Long, kstDate: LocalDate): AdRewardDailyQuota {
        // 멱등 INSERT(ON DUPLICATE KEY UPDATE no-op)로 행을 보장 생성한다. 충돌해도 예외가 없어 메인 트랜잭션이
        // 오염되지 않고, 엔티티를 로드하지 않으므로 findForUpdate 가 행을 락과 함께 최신 상태로 처음 로드한다.
        adRewardDailyQuotaRepository.insertIfAbsent(userId, kstDate)
        return adRewardDailyQuotaRepository.findForUpdate(userId, kstDate)
            ?: throw IllegalStateException("ad_reward_daily_quota row must exist for userId=$userId on $kstDate")
    }

    private companion object {
        private val KST = ZoneId.of("Asia/Seoul")
    }
}
