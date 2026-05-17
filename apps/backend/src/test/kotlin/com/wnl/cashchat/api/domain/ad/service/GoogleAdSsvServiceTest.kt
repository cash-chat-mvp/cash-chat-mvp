package com.wnl.cashchat.api.domain.ad.service

import com.wnl.cashchat.api.domain.ad.exception.InvalidGoogleAdSsvCallbackException
import com.wnl.cashchat.api.domain.ad.persistence.entity.GoogleAdSsvEvent
import com.wnl.cashchat.api.domain.ad.persistence.repository.GoogleAdSsvEventRepository
import com.wnl.cashchat.api.domain.ad.properties.GoogleAdSsvProperties
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException

class GoogleAdSsvServiceTest : FunSpec({
    val rawQuery = "ad_unit=rewarded-ad-unit&reward_amount=10&reward_item=coin&timestamp=1710000000123" +
        "&transaction_id=txn-123&user_id=user-42&signature=sig&key_id=12345"

    fun callback(
        transactionId: String = "txn-123",
        userId: String = "user-42",
        rewardAmount: Int = 10,
        rewardItem: String = "coin",
        adUnit: String = "rewarded-ad-unit",
        keyId: Long = 12345L,
    ) = GoogleAdSsvCallback(
        adUnit = adUnit,
        rewardAmount = rewardAmount,
        rewardItem = rewardItem,
        timestamp = 1710000000123L,
        transactionId = transactionId,
        userId = userId,
        signature = "sig",
        keyId = keyId,
        rawQueryString = rawQuery,
        signedPayload = rawQuery.substringBefore("&signature="),
    )

    fun event(
        transactionId: String = "txn-123",
        userId: String = "user-42",
        rewardAmount: Int = 10,
        rewardItem: String = "coin",
        adUnit: String = "rewarded-ad-unit",
        keyId: Long = 12345L,
    ) = GoogleAdSsvEvent(
        transactionId = transactionId,
        userId = userId,
        rewardAmount = rewardAmount,
        rewardItem = rewardItem,
        adUnit = adUnit,
        keyId = keyId,
        rawQueryString = rawQuery,
    )

    fun service(
        parser: GoogleAdSsvQueryParser = mock(),
        signatureVerifier: GoogleAdSsvSignatureVerifier = mock(),
        repository: GoogleAdSsvEventRepository = mock(),
        properties: GoogleAdSsvProperties = GoogleAdSsvProperties(rewardedAdUnitId = "rewarded-ad-unit"),
    ) = GoogleAdSsvService(
        parser = parser,
        signatureVerifier = signatureVerifier,
        repository = repository,
        properties = properties,
    )

    test("saves verified callback and calls verifier") {
        val parser = mock<GoogleAdSsvQueryParser>()
        val signatureVerifier = mock<GoogleAdSsvSignatureVerifier>()
        val repository = mock<GoogleAdSsvEventRepository>()
        val callback = callback()
        whenever(parser.parse(rawQuery)).thenReturn(callback)
        whenever(repository.findByTransactionId(callback.transactionId)).thenReturn(null)
        whenever(repository.saveAndFlush(any<GoogleAdSsvEvent>())).thenAnswer { it.arguments[0] }
        val service = service(parser, signatureVerifier, repository)

        service.verifyAndStore(rawQuery)

        verify(signatureVerifier).verify(callback.signedPayload, callback.signature, callback.keyId)
        val eventCaptor = argumentCaptor<GoogleAdSsvEvent>()
        verify(repository).saveAndFlush(eventCaptor.capture())
        eventCaptor.firstValue.transactionId shouldBe callback.transactionId
        eventCaptor.firstValue.userId shouldBe callback.userId
        eventCaptor.firstValue.rewardAmount shouldBe callback.rewardAmount
        eventCaptor.firstValue.rewardItem shouldBe callback.rewardItem
        eventCaptor.firstValue.adUnit shouldBe callback.adUnit
        eventCaptor.firstValue.keyId shouldBe callback.keyId
        eventCaptor.firstValue.rawQueryString shouldBe rawQuery
    }

    test("existing transaction id returns success without verifier or save") {
        val parser = mock<GoogleAdSsvQueryParser>()
        val signatureVerifier = mock<GoogleAdSsvSignatureVerifier>()
        val repository = mock<GoogleAdSsvEventRepository>()
        val callback = callback()
        whenever(parser.parse(rawQuery)).thenReturn(callback)
        whenever(repository.findByTransactionId(callback.transactionId)).thenReturn(event(userId = "different-user"))
        val service = service(parser, signatureVerifier, repository)

        service.verifyAndStore(rawQuery)

        verify(signatureVerifier, never()).verify(any(), any(), any())
        verify(repository, never()).saveAndFlush(any<GoogleAdSsvEvent>())
    }

    test("ad unit mismatch rejects and does not save") {
        val parser = mock<GoogleAdSsvQueryParser>()
        val signatureVerifier = mock<GoogleAdSsvSignatureVerifier>()
        val repository = mock<GoogleAdSsvEventRepository>()
        val callback = callback(adUnit = "unexpected-ad-unit")
        whenever(parser.parse(rawQuery)).thenReturn(callback)
        whenever(repository.findByTransactionId(callback.transactionId)).thenReturn(null)
        val service = service(parser, signatureVerifier, repository)

        shouldThrow<InvalidGoogleAdSsvCallbackException> {
            service.verifyAndStore(rawQuery)
        }

        verify(signatureVerifier, never()).verify(any(), any(), any())
        verify(repository, never()).saveAndFlush(any<GoogleAdSsvEvent>())
    }

    test("blank configured rewarded ad unit skips ad unit validation") {
        val parser = mock<GoogleAdSsvQueryParser>()
        val signatureVerifier = mock<GoogleAdSsvSignatureVerifier>()
        val repository = mock<GoogleAdSsvEventRepository>()
        val callback = callback(adUnit = "callback-ad-unit")
        whenever(parser.parse(rawQuery)).thenReturn(callback)
        whenever(repository.findByTransactionId(callback.transactionId)).thenReturn(null)
        whenever(repository.saveAndFlush(any<GoogleAdSsvEvent>())).thenAnswer { it.arguments[0] }
        val service = service(
            parser = parser,
            signatureVerifier = signatureVerifier,
            repository = repository,
            properties = GoogleAdSsvProperties(rewardedAdUnitId = ""),
        )

        service.verifyAndStore(rawQuery)

        verify(signatureVerifier).verify(callback.signedPayload, callback.signature, callback.keyId)
        verify(repository).saveAndFlush(any<GoogleAdSsvEvent>())
    }

    test("concurrent duplicate insert recovers as success") {
        val parser = mock<GoogleAdSsvQueryParser>()
        val signatureVerifier = mock<GoogleAdSsvSignatureVerifier>()
        val repository = mock<GoogleAdSsvEventRepository>()
        val callback = callback()
        whenever(parser.parse(rawQuery)).thenReturn(callback)
        whenever(repository.findByTransactionId(callback.transactionId)).thenReturn(null, event())
        whenever(repository.saveAndFlush(any<GoogleAdSsvEvent>()))
            .thenThrow(DataIntegrityViolationException("duplicate transaction_id"))
        val service = service(parser, signatureVerifier, repository)

        service.verifyAndStore(rawQuery)

        verify(signatureVerifier).verify(callback.signedPayload, callback.signature, callback.keyId)
        verify(repository).saveAndFlush(any<GoogleAdSsvEvent>())
    }
})
