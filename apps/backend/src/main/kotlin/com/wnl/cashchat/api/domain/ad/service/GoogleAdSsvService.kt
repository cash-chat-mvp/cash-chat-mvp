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
    fun verifyAndStore(rawQueryString: String?) {
        val callback = parser.parse(rawQueryString)
        validateAdUnit(callback)
        signatureVerifier.verify(callback.signedPayload, callback.signature, callback.keyId)

        val existingEvent = repository.findByTransactionId(callback.transactionId)
        if (existingEvent != null) {
            logIfCoreFieldsDiffer(callback, existingEvent)
            // Idempotent: credit via ledger (LedgerService itself is idempotent on key)
            creditReward(callback)
            return
        }

        try {
            repository.saveAndFlush(callback.toEntity())
        } catch (exception: DataIntegrityViolationException) {
            val duplicateEvent = repository.findByTransactionId(callback.transactionId)
            if (duplicateEvent != null) {
                logIfCoreFieldsDiffer(callback, duplicateEvent)
                creditReward(callback)
                return
            }
            throw exception
        }

        creditReward(callback)
    }

    /**
     * SSV 검증 성공 후 LedgerService 를 통해 유저에게 보상 적립.
     *
     * grossRevenue = callback.rewardAmount (Google SSV 콜백의 reward_amount).
     * userId = callback.userId.toLong() (클라이언트 SDK 가 우리 내부 user.id 를 전달).
     * idempotencyKey = "ad:ssv:<transactionId>" (SSV transactionId 를 멱등 키로 사용).
     */
    private fun creditReward(callback: GoogleAdSsvCallback) {
        val internalUserId = callback.userId.toLongOrNull()
            ?: run {
                logger.warn("Google Ad SSV userId is not a numeric internal id: {}", callback.userId)
                return
            }
        ledgerService.recordRevenue(
            userId = internalUserId,
            source = RevenueSource.AD,
            grossRevenue = callback.rewardAmount.toLong(),
            idempotencyKey = "ad:ssv:${callback.transactionId}",
        )
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
