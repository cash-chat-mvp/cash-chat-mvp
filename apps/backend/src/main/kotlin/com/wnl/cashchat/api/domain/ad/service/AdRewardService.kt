package com.wnl.cashchat.api.domain.ad.service

import com.wnl.cashchat.api.domain.ad.persistence.entity.AdRewardDailyQuota
import com.wnl.cashchat.api.domain.ad.persistence.entity.RewardStatus
import com.wnl.cashchat.api.domain.ad.persistence.repository.AdRewardDailyQuotaRepository
import com.wnl.cashchat.api.domain.ad.persistence.repository.AdRewardNonceRepository
import com.wnl.cashchat.api.domain.ad.persistence.repository.GoogleAdSsvEventRepository
import com.wnl.cashchat.api.domain.ad.properties.AdRewardProperties
import com.wnl.cashchat.api.domain.point.persistence.entity.PointTransactionReason
import com.wnl.cashchat.api.domain.point.service.UserPointService
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * SSV 서명 검증 통과·신규 저장된 광고 이벤트에 대해 코인을 적립한다.
 * 단일 @Transactional 안에서 nonce 해석 → 일일 한도 행 락 → 코인 적립 → 이벤트 상태 갱신을 원자적으로 수행.
 * callback.userId 는 클라이언트가 SSV user_id 필드에 실은 서버 발급 nonce 다(직접 신뢰 금지).
 */
@Service
class AdRewardService(
    private val googleAdSsvEventRepository: GoogleAdSsvEventRepository,
    private val adRewardNonceRepository: AdRewardNonceRepository,
    private val adRewardDailyQuotaRepository: AdRewardDailyQuotaRepository,
    private val userPointService: UserPointService,
    private val adRewardProperties: AdRewardProperties,
) {
    @Transactional
    fun grantFromCallback(callback: GoogleAdSsvCallback, now: Instant) {
        val event = googleAdSsvEventRepository.findByTransactionId(callback.transactionId) ?: return

        val nonce = adRewardNonceRepository.findById(callback.userId).orElse(null)
        if (nonce == null || !nonce.isUsable(now)) {
            event.markRejected(RewardStatus.REJECTED_INVALID_NONCE)
            return
        }

        val kstDate = LocalDate.ofInstant(now, KST)
        val quota = lockOrCreateQuota(nonce.userId, kstDate)
        if (quota.usedCount >= adRewardProperties.dailyLimit) {
            event.markRejected(RewardStatus.REJECTED_OVER_QUOTA)
            return
        }

        quota.increment()
        nonce.markUsed()
        userPointService.recordTransaction(
            userId = nonce.userId,
            delta = adRewardProperties.coinAmount,
            reason = PointTransactionReason.AD_REWARD,
            idempotencyKey = "admob:reward:${callback.transactionId}",
        )
        event.markGranted()
    }

    private fun lockOrCreateQuota(userId: Long, kstDate: LocalDate): AdRewardDailyQuota {
        adRewardDailyQuotaRepository.findForUpdate(userId, kstDate)?.let { return it }
        return try {
            adRewardDailyQuotaRepository.saveAndFlush(AdRewardDailyQuota(userId = userId, kstDate = kstDate))
            adRewardDailyQuotaRepository.findForUpdate(userId, kstDate)!!
        } catch (e: DataIntegrityViolationException) {
            // 동시에 같은 (userId, kstDate) 행이 INSERT 된 경우: 그 행을 락 잡아 다시 읽는다.
            adRewardDailyQuotaRepository.findForUpdate(userId, kstDate) ?: throw e
        }
    }

    private companion object {
        private val KST = ZoneId.of("Asia/Seoul")
    }
}
