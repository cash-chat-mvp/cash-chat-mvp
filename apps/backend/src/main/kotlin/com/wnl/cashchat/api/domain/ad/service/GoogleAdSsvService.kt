package com.wnl.cashchat.api.domain.ad.service

import com.wnl.cashchat.api.domain.ad.exception.InvalidGoogleAdSsvCallbackException
import com.wnl.cashchat.api.domain.ad.persistence.entity.GoogleAdSsvEvent
import com.wnl.cashchat.api.domain.ad.persistence.repository.GoogleAdSsvEventRepository
import com.wnl.cashchat.api.domain.ad.properties.GoogleAdSsvProperties
import com.wnl.cashchat.api.domain.ledger.persistence.entity.RevenueSource
import com.wnl.cashchat.api.domain.ledger.service.LedgerService
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class GoogleAdSsvService(
    private val parser: GoogleAdSsvQueryParser,
    private val signatureVerifier: GoogleAdSsvSignatureVerifier,
    private val repository: GoogleAdSsvEventRepository,
    private val properties: GoogleAdSsvProperties,
    private val ledgerService: LedgerService,
) {
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun verifyAndStore(rawQueryString: String?): GoogleAdSsvVerificationResult {
        val callback = parser.parse(rawQueryString)
        // 서명 검증을 가장 먼저 수행한다. 이것이 보안 경계이며, 200 응답은 'Google 이 우리 계정용으로
        // 서명한 진짜 콜백'에만 부여된다. AdMob 콜백 URL '확인' 핑도 유효 서명을 싣고 오므로 통과한다.
        signatureVerifier.verify(callback.signedPayload, callback.signature, callback.keyId)

        // ad_unit 불일치는 거절(400)이 아니라 '수신하되 적립하지 않음(200)' 으로 처리한다.
        // AdMob URL '확인' 핑은 실제 광고 단위가 아닌 placeholder ad_unit 을 싣기 때문에 400 으로 막으면
        // URL 등록이 불가능하고, 잘못된 ad_unit 의 (서명 유효) 콜백을 400 으로 돌려주면 Google 이 재시도를 반복한다.
        if (!isAdUnitMatched(callback)) {
            logger.warn(
                "Google Ad SSV ad_unit mismatch — accepted without crediting (callback ad_unit={}, configured={})",
                callback.adUnit,
                properties.rewardedAdUnitId,
            )
            return GoogleAdSsvVerificationResult(callback, newlyStored = false)
        }

        val internalUserId = callback.userId.toLongOrNull()
            ?: throw InvalidGoogleAdSsvCallbackException("Google Ad SSV userId is not a numeric internal id: ${callback.userId}")

        val existingEvent = repository.findByTransactionId(callback.transactionId)
        if (existingEvent != null) {
            logIfCoreFieldsDiffer(callback, existingEvent)
            // 멱등: ledger 적립(키 기반 멱등이라 재호출해도 중복 적립 없음)
            creditReward(internalUserId, callback)
            return GoogleAdSsvVerificationResult(callback, newlyStored = false)
        }

        return try {
            repository.saveAndFlush(callback.toEntity())
            creditReward(internalUserId, callback)
            GoogleAdSsvVerificationResult(callback, newlyStored = true)
        } catch (exception: DataIntegrityViolationException) {
            val duplicateEvent = repository.findByTransactionId(callback.transactionId)
            if (duplicateEvent != null) {
                logIfCoreFieldsDiffer(callback, duplicateEvent)
                creditReward(internalUserId, callback)
                GoogleAdSsvVerificationResult(callback, newlyStored = false)
            } else {
                throw exception
            }
        }
    }

    /**
     * SSV 검증 성공 후 LedgerService 를 통해 유저에게 보상 적립.
     *
     * grossRevenue = callback.rewardAmount (Google SSV 콜백의 reward_amount).
     * internalUserId = callback.userId 로부터 파싱된 내부 user.id (클라이언트 SDK 가 Long 을 문자열로 전달).
     * idempotencyKey = "ad:ssv:<transactionId>" (SSV transactionId 를 멱등 키로 사용).
     *
     * ledgerService.recordRevenue 가 예외를 던지더라도 SSV 검증은 이미 성공했고
     * 이벤트 행도 저장됐으므로 예외를 전파하지 않는다. 전파하면 Google 이 콜백을 재시도하므로
     * 운영 추적을 위해 ERROR 로 기록하고 삼킨다(retry storm 방지).
     */
    private fun creditReward(internalUserId: Long, callback: GoogleAdSsvCallback) {
        try {
            ledgerService.recordRevenue(
                userId = internalUserId,
                source = RevenueSource.AD,
                grossRevenue = callback.rewardAmount.toLong(),
                idempotencyKey = "ad:ssv:${callback.transactionId}",
            )
        } catch (e: Exception) {
            logger.error(
                "Failed to credit reward for SSV transaction {} (userId={}): {}",
                callback.transactionId,
                internalUserId,
                e.message,
                e,
            )
            // 예외를 삼킨다 — SSV endpoint 는 2xx 를 반환하여 Google 의 재시도를 방지한다
        }
    }

    private fun isAdUnitMatched(callback: GoogleAdSsvCallback): Boolean {
        if (!properties.isRewardedAdUnitValidationEnabled()) {
            return true
        }
        return callback.adUnit == properties.rewardedAdUnitId
    }

    private fun GoogleAdSsvCallback.toEntity(): GoogleAdSsvEvent =
        GoogleAdSsvEvent(
            transactionId = transactionId,
            userId = userId,
            rewardAmount = rewardAmount,
            rewardItem = rewardItem,
            adUnit = adUnit,
            keyId = keyId,
            rawQueryString = rawQueryString,
        )

    private fun logIfCoreFieldsDiffer(
        callback: GoogleAdSsvCallback,
        event: GoogleAdSsvEvent,
    ) {
        if (!event.hasSameCoreFieldsAs(callback)) {
            logger.warn(
                "Duplicate Google Ad SSV transaction id {} has different callback fields",
                callback.transactionId,
            )
        }
    }

    private fun GoogleAdSsvEvent.hasSameCoreFieldsAs(callback: GoogleAdSsvCallback): Boolean =
        userId == callback.userId &&
            rewardAmount == callback.rewardAmount &&
            rewardItem == callback.rewardItem &&
            adUnit == callback.adUnit &&
            keyId == callback.keyId

    companion object {
        private val logger = LoggerFactory.getLogger(GoogleAdSsvService::class.java)
    }
}
