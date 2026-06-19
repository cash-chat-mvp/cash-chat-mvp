package com.wnl.cashchat.api.domain.offerwall.service

import com.wnl.cashchat.api.domain.offerwall.persistence.entity.TnkOfferwallStatus
import com.wnl.cashchat.api.domain.offerwall.persistence.repository.TnkOfferwallCallbackRepository
import com.wnl.cashchat.api.domain.offerwall.properties.TnkOfferwallProperties
import com.wnl.cashchat.api.domain.point.persistence.entity.PointTransactionReason
import com.wnl.cashchat.api.domain.point.service.UserPointService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

/**
 * TNK 서버 포스트백을 검증·적립한다. 단일 @Transactional 안에서
 * 서명 검증 → 멱등 INSERT(PENDING) → 행 락 → 토큰 해석 → 환산 적립(멱등키) → status 갱신을 원자적으로 수행한다.
 * 서명 검증을 DB 쓰기보다 먼저 수행해, public 엔드포인트로 들어온 미검증 요청이 원장 행을 만드는 것을 막는다
 * (서명 실패는 로그만 남기고 미기록 — AdMob SSV 패턴과 정합). 서명 통과 콜백만 원장에 기록된다.
 */
@Service
class TnkOfferwallService(
    private val tnkOfferwallCallbackRepository: TnkOfferwallCallbackRepository,
    private val tnkMdChecksumVerifier: TnkMdChecksumVerifier,
    private val offerwallUserTokenService: OfferwallUserTokenService,
    private val userPointService: UserPointService,
    private val tnkOfferwallProperties: TnkOfferwallProperties,
) {
    private val log = LoggerFactory.getLogger(TnkOfferwallService::class.java)

    @Transactional
    fun handleCallback(params: TnkOfferwallCallbackParams, now: Instant): TnkOfferwallStatus {
        // 서명 검증을 DB 쓰기 앞에 둔다 — 미검증 public 요청이 원장 행을 무제한 생성하는 것을 차단.
        if (!tnkMdChecksumVerifier.isValid(params)) {
            log.warn("TNK offerwall callback rejected: bad signature (seqId={})", params.seqId)
            return TnkOfferwallStatus.REJECTED_BAD_SIGNATURE
        }

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

        // 적립액은 양수여야 한다. 음수/0 pay_pnt 를 그대로 적립하면 recordTransaction 이 차감으로 처리해
        // 포인트가 사라진다(환수는 범위 외). 비정상 콜백은 기록만 하고 적립하지 않는다.
        if (params.payPnt <= 0) {
            log.warn("TNK offerwall callback rejected: non-positive pay_pnt={} (seqId={})", params.payPnt, params.seqId)
            callback.markRejected(TnkOfferwallStatus.REJECTED_NON_POSITIVE)
            return TnkOfferwallStatus.REJECTED_NON_POSITIVE
        }

        val userId = offerwallUserTokenService.resolveUserId(params.mdUserNm)
        if (userId == null) {
            log.warn("TNK offerwall callback rejected: unknown token (seqId={})", params.seqId)
            callback.markRejected(TnkOfferwallStatus.REJECTED_UNKNOWN_USER)
            return TnkOfferwallStatus.REJECTED_UNKNOWN_USER
        }

        // 환산비가 1 미만이면 floor 후 0 코인이 될 수 있다(예: pay_pnt=1, ratio=0.5). 적립액이 0이면
        // recordTransaction 을 건너뛰어 불필요한 0원 트랜잭션을 남기지 않는다. 콜백 행은 멱등성을 위해
        // GRANTED(coinAmount=0)로 종결한다(seq_id 재전송 시 재처리 안 함).
        val coinAmount = toCoinAmount(params.payPnt, tnkOfferwallProperties.pointToCoinRatio)
        if (coinAmount > 0) {
            userPointService.recordTransaction(
                userId = userId,
                delta = coinAmount,
                reason = PointTransactionReason.OFFERWALL,
                idempotencyKey = "tnk:offerwall:${params.seqId}",
            )
        }
        callback.markGranted(userId = userId, coinAmount = coinAmount)
        return TnkOfferwallStatus.GRANTED
    }
}

/**
 * pay_pnt × 환산비를 코인으로 환산한다. Double 곱셈은 정밀도 손실(예: 50 × 0.58 = 28.999…)로
 * floor 시 1코인 적게 적립될 수 있으므로 BigDecimal + RoundingMode.FLOOR 로 계산한다.
 * 결과가 Long 범위를 넘으면 toLongExact() 가 예외를 던진다 — toLong() 의 조용한 wrap 으로
 * 음수 coinAmount 가 만들어져 차감되는 사고를 막는다(과대 pay_pnt/ratio 방어).
 */
internal fun toCoinAmount(payPnt: Long, ratio: Double): Long =
    BigDecimal.valueOf(payPnt)
        .multiply(BigDecimal.valueOf(ratio))
        .setScale(0, RoundingMode.FLOOR)
        .longValueExact()
