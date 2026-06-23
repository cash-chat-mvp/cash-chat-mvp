package com.wnl.cashchat.api.domain.ad.service

import com.wnl.cashchat.api.domain.ad.exception.InvalidGoogleAdSsvCallbackException
import com.wnl.cashchat.api.domain.ad.persistence.entity.GoogleAdSsvEvent
import com.wnl.cashchat.api.domain.ad.persistence.repository.GoogleAdSsvEventRepository
import com.wnl.cashchat.api.domain.ad.properties.GoogleAdSsvProperties
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * Google AdMob SSV 콜백을 검증(서명·광고단위)하고 멱등하게 저장한다.
 *
 * 적립은 본 서비스가 하지 않는다. 컨트롤러가 검증 성공 결과를 AdRewardService.grantFromCallback 에 전달하면,
 * 거기서 callback.userId(=서버 발급 nonce)를 내부 userId 로 해석해 일일 한도·단일 사용 nonce 를 검증하며 코인을 적립한다.
 * 따라서 여기서는 callback.userId 를 숫자로 강제하지 않는다(nonce 는 비숫자 opaque 토큰).
 */
@Service
class GoogleAdSsvService(
    private val parser: GoogleAdSsvQueryParser,
    private val signatureVerifier: GoogleAdSsvSignatureVerifier,
    private val repository: GoogleAdSsvEventRepository,
    private val properties: GoogleAdSsvProperties,
) {
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun verifyAndStore(rawQueryString: String?): GoogleAdSsvVerificationResult {
        // 공개 진입점에서 fail-fast: null/blank 는 파서 구현에 의존하지 않고 여기서 명확히 거절한다.
        if (rawQueryString.isNullOrBlank()) {
            throw InvalidGoogleAdSsvCallbackException("Google Ad SSV raw query string is null or blank")
        }
        val callback = parser.parse(rawQueryString)
        validateAdUnit(callback)
        signatureVerifier.verify(callback.signedPayload, callback.signature, callback.keyId)

        val existingEvent = repository.findByTransactionId(callback.transactionId)
        if (existingEvent != null) {
            logIfCoreFieldsDiffer(callback, existingEvent)
            return GoogleAdSsvVerificationResult(callback, newlyStored = false)
        }

        return try {
            repository.saveAndFlush(callback.toEntity())
            GoogleAdSsvVerificationResult(callback, newlyStored = true)
        } catch (exception: DataIntegrityViolationException) {
            val duplicateEvent = repository.findByTransactionId(callback.transactionId)
            if (duplicateEvent != null) {
                logIfCoreFieldsDiffer(callback, duplicateEvent)
                GoogleAdSsvVerificationResult(callback, newlyStored = false)
            } else {
                // transaction_id 중복이 아닌 예기치 못한 제약 위반 — 멱등 복구가 불가능하므로
                // 진단을 위해 ERROR 로 남기고 그대로 전파한다(상위에서 처리/관측).
                logger.error(
                    "Unexpected DataIntegrityViolationException for Google Ad SSV transaction {}",
                    callback.transactionId,
                    exception,
                )
                throw exception
            }
        }
    }

    private fun validateAdUnit(callback: GoogleAdSsvCallback) {
        if (!properties.isRewardedAdUnitValidationEnabled()) {
            return
        }
        if (callback.adUnit != properties.rewardedAdUnitId) {
            throw InvalidGoogleAdSsvCallbackException("Google Ad SSV ad_unit does not match configured rewarded ad unit")
        }
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
