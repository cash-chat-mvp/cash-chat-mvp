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
 * 거기서 callback.customData(=서버 발급 nonce)를 내부 userId 로 해석해 일일 한도·단일 사용 nonce 를 검증하며 코인을 적립한다.
 * FE 는 SSV custom_data 로 nonce 를 싣고 user_id 는 보내지 않으므로, 여기서는 user_id 를 강제하지 않는다(옵셔널).
 *
 * HTTP 응답 정책: 서명이 유효한 콜백에는 200 을 반환한다. ad_unit 불일치는 거절(400)이 아니라
 * '수신하되 적립하지 않음'(미저장, 200)으로 처리한다 — AdMob 콜백 URL '확인' 핑이 placeholder ad_unit 을
 * 싣기 때문에 400 으로 막으면 URL 등록이 불가능하고, 잘못된 ad_unit 의 (서명 유효) 콜백을 400 으로
 * 돌려주면 Google 이 재시도를 반복한다. 따라서 서명 검증을 먼저(보안 경계) 수행한 뒤 ad_unit 을 판정한다.
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
        // 서명 검증을 가장 먼저 수행한다. 이것이 보안 경계이며, 200 응답은 'Google 이 우리 계정용으로
        // 서명한 진짜 콜백'에만 부여된다. AdMob 콜백 URL '확인' 핑도 유효 서명을 싣고 오므로 통과한다.
        signatureVerifier.verify(callback.signedPayload, callback.signature, callback.keyId)

        // ad_unit 불일치는 거절(400)이 아니라 '수신하되 적립하지 않음(200)' 으로 처리한다(미저장).
        // AdMob URL '확인' 핑은 실제 광고 단위가 아닌 placeholder ad_unit 을 싣기 때문에 400 으로 막으면
        // URL 등록이 불가능하고, 잘못된 ad_unit 의 (서명 유효) 콜백을 400 으로 돌려주면 Google 이 재시도를 반복한다.
        // 실제 ad_unit 값을 로그로 남겨 AdMob 확인 핑이 보내는 값을 관측할 수 있게 한다.
        if (!isAdUnitMatched(callback)) {
            logger.warn(
                "Google Ad SSV ad_unit mismatch — accepted without crediting (callback ad_unit={}, configured={})",
                callback.adUnit,
                properties.rewardedAdUnitIds,
            )
            return GoogleAdSsvVerificationResult(callback, newlyStored = false)
        }

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

    private fun isAdUnitMatched(callback: GoogleAdSsvCallback): Boolean {
        if (!properties.isRewardedAdUnitValidationEnabled()) {
            return true
        }
        return properties.isAllowedAdUnit(callback.adUnit)
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
            customData = customData,
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
            keyId == callback.keyId &&
            customData == callback.customData

    companion object {
        private val logger = LoggerFactory.getLogger(GoogleAdSsvService::class.java)
    }
}
