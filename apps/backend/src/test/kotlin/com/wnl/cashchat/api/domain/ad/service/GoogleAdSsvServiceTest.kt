package com.wnl.cashchat.api.domain.ad.service

import com.wnl.cashchat.api.domain.ad.exception.InvalidGoogleAdSsvCallbackException
import com.wnl.cashchat.api.domain.ad.persistence.entity.GoogleAdSsvEvent
import com.wnl.cashchat.api.domain.ad.persistence.repository.GoogleAdSsvEventRepository
import com.wnl.cashchat.api.domain.ad.properties.GoogleAdSsvProperties
import com.wnl.cashchat.api.domain.ledger.persistence.entity.RevenueSource
import com.wnl.cashchat.api.domain.ledger.service.LedgerService
import com.wnl.cashchat.api.domain.ledger.service.RevenueDistribution
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.times
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

class GoogleAdSsvServiceTest : FunSpec({
    // numeric userId so ledger crediting works (client sends internal Long id as string)
    val numericUserId = "42"
    val rawQuery = "ad_unit=rewarded-ad-unit&reward_amount=10&reward_item=coin&timestamp=1710000000123" +
        "&transaction_id=txn-123&user_id=$numericUserId&signature=sig&key_id=12345"

    fun callback(
        transactionId: String = "txn-123",
        userId: String = numericUserId,
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
        userId: String = numericUserId,
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

    val stubDistribution = RevenueDistribution(
        grossRevenue = 10L,
        riskReserve = 1L,
        serviceReserve = 0L,
        companyProfit = 2L,
        cashablePt = 4L,
        energy = 3,
    )

    fun ledgerMock() = mock<LedgerService>().also {
        whenever(it.recordRevenue(any(), any(), any(), any())).thenReturn(stubDistribution)
    }

    fun service(
        parser: GoogleAdSsvQueryParser = mock(),
        signatureVerifier: GoogleAdSsvSignatureVerifier = mock(),
        repository: GoogleAdSsvEventRepository = mock(),
        properties: GoogleAdSsvProperties = GoogleAdSsvProperties(rewardedAdUnitId = "rewarded-ad-unit"),
        ledgerService: LedgerService = ledgerMock(),
    ) = GoogleAdSsvService(
        parser = parser,
        signatureVerifier = signatureVerifier,
        repository = repository,
        properties = properties,
        ledgerService = ledgerService,
    )

    test("saves verified callback, calls verifier, and credits reward via ledger") {
        val parser = mock<GoogleAdSsvQueryParser>()
        val signatureVerifier = mock<GoogleAdSsvSignatureVerifier>()
        val repository = mock<GoogleAdSsvEventRepository>()
        val ledgerService = ledgerMock()
        val callback = callback()
        whenever(parser.parse(rawQuery)).thenReturn(callback)
        whenever(repository.findByTransactionId(callback.transactionId)).thenReturn(null)
        whenever(repository.saveAndFlush(any<GoogleAdSsvEvent>())).thenAnswer { it.arguments[0] }
        val service = service(parser, signatureVerifier, repository, ledgerService = ledgerService)

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

        verify(ledgerService).recordRevenue(
            eq(42L),
            eq(RevenueSource.AD),
            eq(10L),
            eq("ad:ssv:txn-123"),
        )
    }

    test("existing transaction id validates signature before returning success without save, credits via ledger") {
        val parser = mock<GoogleAdSsvQueryParser>()
        val signatureVerifier = mock<GoogleAdSsvSignatureVerifier>()
        val repository = mock<GoogleAdSsvEventRepository>()
        val ledgerService = ledgerMock()
        val callback = callback()
        whenever(parser.parse(rawQuery)).thenReturn(callback)
        whenever(repository.findByTransactionId(callback.transactionId)).thenReturn(event(userId = "99"))
        val service = service(parser, signatureVerifier, repository, ledgerService = ledgerService)

        service.verifyAndStore(rawQuery)

        verify(signatureVerifier).verify(callback.signedPayload, callback.signature, callback.keyId)
        verify(repository, never()).saveAndFlush(any<GoogleAdSsvEvent>())
        // LedgerService is still called (it handles idempotency internally)
        verify(ledgerService).recordRevenue(eq(42L), eq(RevenueSource.AD), eq(10L), eq("ad:ssv:txn-123"))
    }

    test("existing transaction id with invalid signature is rejected without save") {
        val parser = mock<GoogleAdSsvQueryParser>()
        val signatureVerifier = mock<GoogleAdSsvSignatureVerifier>()
        val repository = mock<GoogleAdSsvEventRepository>()
        val ledgerService = ledgerMock()
        val callback = callback()
        whenever(parser.parse(rawQuery)).thenReturn(callback)
        whenever(repository.findByTransactionId(callback.transactionId)).thenReturn(event())
        doThrow(InvalidGoogleAdSsvCallbackException("Invalid Google AdMob SSV signature"))
            .whenever(signatureVerifier)
            .verify(callback.signedPayload, callback.signature, callback.keyId)
        val service = service(parser, signatureVerifier, repository, ledgerService = ledgerService)

        shouldThrow<InvalidGoogleAdSsvCallbackException> {
            service.verifyAndStore(rawQuery)
        }

        verify(repository, never()).saveAndFlush(any<GoogleAdSsvEvent>())
        verify(ledgerService, never()).recordRevenue(any(), any(), any(), any())
    }

    test("existing transaction id with wrong key is rejected without save") {
        val parser = mock<GoogleAdSsvQueryParser>()
        val signatureVerifier = mock<GoogleAdSsvSignatureVerifier>()
        val repository = mock<GoogleAdSsvEventRepository>()
        val ledgerService = ledgerMock()
        val callback = callback(keyId = 99999L)
        whenever(parser.parse(rawQuery)).thenReturn(callback)
        whenever(repository.findByTransactionId(callback.transactionId)).thenReturn(event())
        doThrow(InvalidGoogleAdSsvCallbackException("Failed to verify Google AdMob SSV signature"))
            .whenever(signatureVerifier)
            .verify(callback.signedPayload, callback.signature, callback.keyId)
        val service = service(parser, signatureVerifier, repository, ledgerService = ledgerService)

        shouldThrow<InvalidGoogleAdSsvCallbackException> {
            service.verifyAndStore(rawQuery)
        }

        verify(repository, never()).saveAndFlush(any<GoogleAdSsvEvent>())
        verify(ledgerService, never()).recordRevenue(any(), any(), any(), any())
    }

    test("existing transaction id with ad unit mismatch is rejected before verifier and save") {
        val parser = mock<GoogleAdSsvQueryParser>()
        val signatureVerifier = mock<GoogleAdSsvSignatureVerifier>()
        val repository = mock<GoogleAdSsvEventRepository>()
        val ledgerService = ledgerMock()
        val callback = callback(adUnit = "unexpected-ad-unit")
        whenever(parser.parse(rawQuery)).thenReturn(callback)
        whenever(repository.findByTransactionId(callback.transactionId)).thenReturn(event())
        val service = service(parser, signatureVerifier, repository, ledgerService = ledgerService)

        shouldThrow<InvalidGoogleAdSsvCallbackException> {
            service.verifyAndStore(rawQuery)
        }

        verify(signatureVerifier, never()).verify(any(), any(), any())
        verify(repository, never()).saveAndFlush(any<GoogleAdSsvEvent>())
        verify(ledgerService, never()).recordRevenue(any(), any(), any(), any())
    }

    test("ad unit mismatch rejects and does not save") {
        val parser = mock<GoogleAdSsvQueryParser>()
        val signatureVerifier = mock<GoogleAdSsvSignatureVerifier>()
        val repository = mock<GoogleAdSsvEventRepository>()
        val ledgerService = ledgerMock()
        val callback = callback(adUnit = "unexpected-ad-unit")
        whenever(parser.parse(rawQuery)).thenReturn(callback)
        whenever(repository.findByTransactionId(callback.transactionId)).thenReturn(null)
        val service = service(parser, signatureVerifier, repository, ledgerService = ledgerService)

        shouldThrow<InvalidGoogleAdSsvCallbackException> {
            service.verifyAndStore(rawQuery)
        }

        verify(signatureVerifier, never()).verify(any(), any(), any())
        verify(repository, never()).saveAndFlush(any<GoogleAdSsvEvent>())
        verify(ledgerService, never()).recordRevenue(any(), any(), any(), any())
    }

    test("blank configured rewarded ad unit skips ad unit validation") {
        val parser = mock<GoogleAdSsvQueryParser>()
        val signatureVerifier = mock<GoogleAdSsvSignatureVerifier>()
        val repository = mock<GoogleAdSsvEventRepository>()
        val ledgerService = ledgerMock()
        val callback = callback(adUnit = "callback-ad-unit")
        whenever(parser.parse(rawQuery)).thenReturn(callback)
        whenever(repository.findByTransactionId(callback.transactionId)).thenReturn(null)
        whenever(repository.saveAndFlush(any<GoogleAdSsvEvent>())).thenAnswer { it.arguments[0] }
        val service = service(
            parser = parser,
            signatureVerifier = signatureVerifier,
            repository = repository,
            properties = GoogleAdSsvProperties(rewardedAdUnitId = ""),
            ledgerService = ledgerService,
        )

        service.verifyAndStore(rawQuery)

        verify(signatureVerifier).verify(callback.signedPayload, callback.signature, callback.keyId)
        verify(repository).saveAndFlush(any<GoogleAdSsvEvent>())
        verify(ledgerService).recordRevenue(eq(42L), eq(RevenueSource.AD), eq(10L), eq("ad:ssv:txn-123"))
    }

    test("concurrent duplicate insert recovers as success and credits via ledger") {
        val parser = mock<GoogleAdSsvQueryParser>()
        val signatureVerifier = mock<GoogleAdSsvSignatureVerifier>()
        val repository = mock<GoogleAdSsvEventRepository>()
        val ledgerService = ledgerMock()
        val callback = callback()
        whenever(parser.parse(rawQuery)).thenReturn(callback)
        whenever(repository.findByTransactionId(callback.transactionId)).thenReturn(null, event())
        whenever(repository.saveAndFlush(any<GoogleAdSsvEvent>()))
            .thenThrow(DataIntegrityViolationException("duplicate transaction_id"))
        val service = service(parser, signatureVerifier, repository, ledgerService = ledgerService)

        service.verifyAndStore(rawQuery)

        verify(signatureVerifier).verify(callback.signedPayload, callback.signature, callback.keyId)
        verify(repository).saveAndFlush(any<GoogleAdSsvEvent>())
        verify(repository, times(2)).findByTransactionId(callback.transactionId)
        verify(ledgerService).recordRevenue(eq(42L), eq(RevenueSource.AD), eq(10L), eq("ad:ssv:txn-123"))
    }

    test("verify and store does not open an outer transaction around duplicate recovery") {
        val annotation = GoogleAdSsvService::class.java
            .getMethod("verifyAndStore", String::class.java)
            .getAnnotation(Transactional::class.java)

        annotation.propagation shouldBe Propagation.NOT_SUPPORTED
    }

    test("query parser is registered as a spring component") {
        (GoogleAdSsvQueryParser::class.java.getAnnotation(Component::class.java) != null) shouldBe true
    }

    test("non-numeric userId in callback is silently skipped without ledger credit") {
        val parser = mock<GoogleAdSsvQueryParser>()
        val signatureVerifier = mock<GoogleAdSsvSignatureVerifier>()
        val repository = mock<GoogleAdSsvEventRepository>()
        val ledgerService = mock<LedgerService>()
        val callbackNonNumeric = callback(userId = "non-numeric-id")
        whenever(parser.parse(rawQuery)).thenReturn(callbackNonNumeric)
        whenever(repository.findByTransactionId(callbackNonNumeric.transactionId)).thenReturn(null)
        whenever(repository.saveAndFlush(any<GoogleAdSsvEvent>())).thenAnswer { it.arguments[0] }
        val service = service(parser, signatureVerifier, repository, ledgerService = ledgerService)

        service.verifyAndStore(rawQuery)

        verify(repository).saveAndFlush(any<GoogleAdSsvEvent>())
        verify(ledgerService, never()).recordRevenue(any(), any(), any(), any())
    }
})
