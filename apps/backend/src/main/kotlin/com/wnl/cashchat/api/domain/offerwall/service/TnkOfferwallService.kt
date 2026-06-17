package com.wnl.cashchat.api.domain.offerwall.service

import com.wnl.cashchat.api.domain.offerwall.persistence.entity.TnkOfferwallStatus
import com.wnl.cashchat.api.domain.offerwall.persistence.repository.TnkOfferwallCallbackRepository
import com.wnl.cashchat.api.domain.offerwall.properties.TnkOfferwallProperties
import com.wnl.cashchat.api.domain.point.persistence.entity.PointTransactionReason
import com.wnl.cashchat.api.domain.point.service.UserPointService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import kotlin.math.floor

/**
 * TNK 서버 포스트백을 검증·적립한다. 단일 @Transactional 안에서
 * 멱등 INSERT(PENDING) → 행 락 → 서명 검증 → 토큰 해석 → 환산 적립(멱등키) → status 갱신을 원자적으로 수행한다.
 * 모든 콜백(거절 포함)은 원장에 기록된다(자동 환수는 범위 외, status 확장으로 후속 대응).
 */
@Service
class TnkOfferwallService(
    private val tnkOfferwallCallbackRepository: TnkOfferwallCallbackRepository,
    private val tnkMdChecksumVerifier: TnkMdChecksumVerifier,
    private val offerwallUserTokenService: OfferwallUserTokenService,
    private val userPointService: UserPointService,
    private val tnkOfferwallProperties: TnkOfferwallProperties,
) {
    @Transactional
    fun handleCallback(params: TnkOfferwallCallbackParams, now: Instant): TnkOfferwallStatus {
        tnkOfferwallCallbackRepository.insertIfAbsent(
            seqId = params.seqId,
            mdUserNm = params.mdUserNm,
            payPnt = params.payPnt,
            rawQuery = params.rawQuery,
        )
        val callback = tnkOfferwallCallbackRepository.findForUpdate(params.seqId)
            ?: throw IllegalStateException("tnk_offerwall_callbacks row must exist for seqId=${params.seqId}")

        // PENDING 만 처리한다. 이미 GRANTED/REJECTED 인 행은 중복/동시 콜백이므로 상태를 그대로 멱등 반환.
        if (callback.status != TnkOfferwallStatus.PENDING) {
            return callback.status
        }

        if (!tnkMdChecksumVerifier.isValid(params)) {
            callback.markRejected(TnkOfferwallStatus.REJECTED_BAD_SIGNATURE)
            return TnkOfferwallStatus.REJECTED_BAD_SIGNATURE
        }

        val userId = offerwallUserTokenService.resolveUserId(params.mdUserNm)
        if (userId == null) {
            callback.markRejected(TnkOfferwallStatus.REJECTED_UNKNOWN_USER)
            return TnkOfferwallStatus.REJECTED_UNKNOWN_USER
        }

        val coinAmount = floor(params.payPnt.toDouble() * tnkOfferwallProperties.pointToCoinRatio).toLong()
        userPointService.recordTransaction(
            userId = userId,
            delta = coinAmount,
            reason = PointTransactionReason.OFFERWALL,
            idempotencyKey = "tnk:offerwall:${params.seqId}",
        )
        callback.markGranted(userId = userId, coinAmount = coinAmount)
        return TnkOfferwallStatus.GRANTED
    }
}
