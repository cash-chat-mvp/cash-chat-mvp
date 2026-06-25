package com.wnl.cashchat.api.domain.ad.service

import com.wnl.cashchat.api.domain.ad.exception.InvalidGoogleAdSsvCallbackException
import com.wnl.cashchat.api.domain.ad.persistence.entity.GoogleAdSsvEvent
import com.wnl.cashchat.api.domain.ad.persistence.repository.GoogleAdSsvEventRepository
import com.wnl.cashchat.api.domain.ad.properties.GoogleAdSsvProperties
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.times
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

class GoogleAdSsvServiceTest : FunSpec({
    // 콜백 timestamp(1710000000123L)와 같은 시각 → 기본 윈도우 안에서 신선.
    val now = Instant.ofEpochMilli(1710000000123L)
    // SSV user_id 에는 서버 발급 nonce(비숫자 opaque 토큰)가 실린다. 적립은 컨트롤러가 이어 호출하는
    // AdRewardService.grantFromCallback(nonce → userId 해석)이 전담하며, GoogleAdSsvService 는 검증·저장만 한다.
    val nonceUserId = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6"
    val rawQuery = "ad_unit=rewarded-ad-unit&reward_amount=10&reward_item=coin&timestamp=1710000000123" +
        "&transaction_id=txn-123&user_id=$nonceUserId&signature=sig&key_id=12345"

    fun callback(
        transactionId: String = "txn-123",
        userId: String = nonceUserId,
        rewardAmount: Int = 10,
        rewardItem: String = "coin",
        adUnit: String = "rewarded-ad-unit",
        keyId: Long = 12345L,
        customData: String? = "custom-nonce",
    ) = GoogleAdSsvCallback(
        adUnit = adUnit,
        rewardAmount = rewardAmount,
        rewardItem = rewardItem,
        timestamp = 1710000000123L,
        transactionId = transactionId,
        userId = userId,
        customData = customData,
        signature = "sig",
        keyId = keyId,
        rawQueryString = rawQuery,
        signedPayload = rawQuery.substringBefore("&signature="),
    )

    fun event(
        transactionId: String = "txn-123",
        userId: String = nonceUserId,
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
        properties: GoogleAdSsvProperties = GoogleAdSsvProperties(rewardedAdUnitIds = listOf("rewarded-ad-unit")),
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

        val result = service.verifyAndStore(rawQuery, now)

        result.newlyStored shouldBe true
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
        eventCaptor.firstValue.customData shouldBe callback.customData
    }

    test("non-numeric nonce userId is accepted, verified, and stored") {
        val parser = mock<GoogleAdSsvQueryParser>()
        val signatureVerifier = mock<GoogleAdSsvSignatureVerifier>()
        val repository = mock<GoogleAdSsvEventRepository>()
        // SSV user_id 는 서버 발급 nonce(비숫자)다. 숫자 강제 없이 그대로 검증·저장돼야 한다.
        val callbackNonce = callback(userId = nonceUserId)
        whenever(parser.parse(rawQuery)).thenReturn(callbackNonce)
        whenever(repository.findByTransactionId(callbackNonce.transactionId)).thenReturn(null)
        whenever(repository.saveAndFlush(any<GoogleAdSsvEvent>())).thenAnswer { it.arguments[0] }
        val service = service(parser, signatureVerifier, repository)

        val result = service.verifyAndStore(rawQuery, now)

        result.newlyStored shouldBe true
        verify(signatureVerifier).verify(callbackNonce.signedPayload, callbackNonce.signature, callbackNonce.keyId)
        val eventCaptor = argumentCaptor<GoogleAdSsvEvent>()
        verify(repository).saveAndFlush(eventCaptor.capture())
        eventCaptor.firstValue.userId shouldBe nonceUserId
    }

    test("existing transaction id validates signature before returning success without save") {
        val parser = mock<GoogleAdSsvQueryParser>()
        val signatureVerifier = mock<GoogleAdSsvSignatureVerifier>()
        val repository = mock<GoogleAdSsvEventRepository>()
        val callback = callback()
        whenever(parser.parse(rawQuery)).thenReturn(callback)
        whenever(repository.findByTransactionId(callback.transactionId)).thenReturn(event(userId = "99deadbeef"))
        val service = service(parser, signatureVerifier, repository)

        val result = service.verifyAndStore(rawQuery, now)

        result.newlyStored shouldBe false
        verify(signatureVerifier).verify(callback.signedPayload, callback.signature, callback.keyId)
        verify(repository, never()).saveAndFlush(any<GoogleAdSsvEvent>())
    }

    test("existing transaction id with invalid signature is rejected without save") {
        val parser = mock<GoogleAdSsvQueryParser>()
        val signatureVerifier = mock<GoogleAdSsvSignatureVerifier>()
        val repository = mock<GoogleAdSsvEventRepository>()
        val callback = callback()
        whenever(parser.parse(rawQuery)).thenReturn(callback)
        whenever(repository.findByTransactionId(callback.transactionId)).thenReturn(event())
        doThrow(InvalidGoogleAdSsvCallbackException("Invalid Google AdMob SSV signature"))
            .whenever(signatureVerifier)
            .verify(callback.signedPayload, callback.signature, callback.keyId)
        val service = service(parser, signatureVerifier, repository)

        shouldThrow<InvalidGoogleAdSsvCallbackException> {
            service.verifyAndStore(rawQuery, now)
        }

        verify(repository, never()).saveAndFlush(any<GoogleAdSsvEvent>())
    }

    test("existing transaction id with wrong key is rejected without save") {
        val parser = mock<GoogleAdSsvQueryParser>()
        val signatureVerifier = mock<GoogleAdSsvSignatureVerifier>()
        val repository = mock<GoogleAdSsvEventRepository>()
        val callback = callback(keyId = 99999L)
        whenever(parser.parse(rawQuery)).thenReturn(callback)
        whenever(repository.findByTransactionId(callback.transactionId)).thenReturn(event())
        doThrow(InvalidGoogleAdSsvCallbackException("Failed to verify Google AdMob SSV signature"))
            .whenever(signatureVerifier)
            .verify(callback.signedPayload, callback.signature, callback.keyId)
        val service = service(parser, signatureVerifier, repository)

        shouldThrow<InvalidGoogleAdSsvCallbackException> {
            service.verifyAndStore(rawQuery, now)
        }

        verify(repository, never()).saveAndFlush(any<GoogleAdSsvEvent>())
    }

    test("ad unit mismatch verifies signature then accepts (200) without storing") {
        val parser = mock<GoogleAdSsvQueryParser>()
        val signatureVerifier = mock<GoogleAdSsvSignatureVerifier>()
        val repository = mock<GoogleAdSsvEventRepository>()
        val callback = callback(adUnit = "unexpected-ad-unit")
        whenever(parser.parse(rawQuery)).thenReturn(callback)
        val service = service(parser, signatureVerifier, repository)

        val result = service.verifyAndStore(rawQuery, now)

        result.newlyStored shouldBe false
        verify(signatureVerifier).verify(callback.signedPayload, callback.signature, callback.keyId)
        verify(repository, never()).saveAndFlush(any<GoogleAdSsvEvent>())
        verify(repository, never()).findByTransactionId(any())
    }

    test("invalid signature is rejected even when ad_unit also mismatches (signature checked first)") {
        val parser = mock<GoogleAdSsvQueryParser>()
        val signatureVerifier = mock<GoogleAdSsvSignatureVerifier>()
        val repository = mock<GoogleAdSsvEventRepository>()
        val callback = callback(adUnit = "unexpected-ad-unit")
        whenever(parser.parse(rawQuery)).thenReturn(callback)
        doThrow(InvalidGoogleAdSsvCallbackException("Invalid Google AdMob SSV signature"))
            .whenever(signatureVerifier)
            .verify(callback.signedPayload, callback.signature, callback.keyId)
        val service = service(parser, signatureVerifier, repository)

        shouldThrow<InvalidGoogleAdSsvCallbackException> {
            service.verifyAndStore(rawQuery, now)
        }

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
            properties = GoogleAdSsvProperties(rewardedAdUnitIds = emptyList()),
        )

        service.verifyAndStore(rawQuery, now)

        verify(signatureVerifier).verify(callback.signedPayload, callback.signature, callback.keyId)
        verify(repository).saveAndFlush(any<GoogleAdSsvEvent>())
    }

    test("accepts a callback from any configured ad unit (Android and iOS)") {
        val parser = mock<GoogleAdSsvQueryParser>()
        val signatureVerifier = mock<GoogleAdSsvSignatureVerifier>()
        val repository = mock<GoogleAdSsvEventRepository>()
        val androidAdUnit = "ca-app-pub-5280178196982923/6512984753"
        val iosAdUnit = "ca-app-pub-5280178196982923/2647937531"
        // 콜백은 iOS 광고 단위에서 들어오지만 두 플랫폼 ID 가 모두 허용 목록에 있어야 저장된다.
        val callback = callback(adUnit = iosAdUnit)
        whenever(parser.parse(rawQuery)).thenReturn(callback)
        whenever(repository.findByTransactionId(callback.transactionId)).thenReturn(null)
        whenever(repository.saveAndFlush(any<GoogleAdSsvEvent>())).thenAnswer { it.arguments[0] }
        val service = service(
            parser = parser,
            signatureVerifier = signatureVerifier,
            repository = repository,
            properties = GoogleAdSsvProperties(rewardedAdUnitIds = listOf(androidAdUnit, iosAdUnit)),
        )

        val result = service.verifyAndStore(rawQuery, now)

        result.newlyStored shouldBe true
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

        val result = service.verifyAndStore(rawQuery, now)

        result.newlyStored shouldBe false
        verify(signatureVerifier).verify(callback.signedPayload, callback.signature, callback.keyId)
        verify(repository).saveAndFlush(any<GoogleAdSsvEvent>())
        verify(repository, times(2)).findByTransactionId(callback.transactionId)
    }

    test("verify and store does not open an outer transaction around duplicate recovery") {
        val annotation = GoogleAdSsvService::class.java
            .getMethod("verifyAndStore", String::class.java, Instant::class.java)
            .getAnnotation(Transactional::class.java)

        annotation.propagation shouldBe Propagation.NOT_SUPPORTED
    }

    test("query parser is registered as a spring component") {
        (GoogleAdSsvQueryParser::class.java.getAnnotation(Component::class.java) != null) shouldBe true
    }

    test("null or blank raw query string is rejected as invalid callback before parsing") {
        val parser = mock<GoogleAdSsvQueryParser>()
        val service = service(parser = parser)

        shouldThrow<InvalidGoogleAdSsvCallbackException> { service.verifyAndStore(null, now) }
        shouldThrow<InvalidGoogleAdSsvCallbackException> { service.verifyAndStore("   ", now) }

        verify(parser, never()).parse(any())
    }

    test("stale timestamp is verified but accepted without storing") {
        val parser = mock<GoogleAdSsvQueryParser>()
        val signatureVerifier = mock<GoogleAdSsvSignatureVerifier>()
        val repository = mock<GoogleAdSsvEventRepository>()
        val callback = callback()
        whenever(parser.parse(rawQuery)).thenReturn(callback)
        val service = service(parser, signatureVerifier, repository)
        // 이벤트 시각보다 2시간 뒤 → 과거 tolerance(1h) 초과.
        val later = Instant.ofEpochMilli(callback.timestamp).plus(Duration.ofHours(2))

        val result = service.verifyAndStore(rawQuery, later)

        result.newlyStored shouldBe false
        verify(signatureVerifier).verify(callback.signedPayload, callback.signature, callback.keyId)
        verify(repository, never()).saveAndFlush(any<GoogleAdSsvEvent>())
        verify(repository, never()).findByTransactionId(any())
    }

    test("timestamp too far in the future is accepted without storing") {
        val parser = mock<GoogleAdSsvQueryParser>()
        val signatureVerifier = mock<GoogleAdSsvSignatureVerifier>()
        val repository = mock<GoogleAdSsvEventRepository>()
        val callback = callback()
        whenever(parser.parse(rawQuery)).thenReturn(callback)
        val service = service(parser, signatureVerifier, repository)
        // 현재 시각이 이벤트 시각보다 10분 전 → 이벤트가 미래 skew(5m) 초과.
        val earlier = Instant.ofEpochMilli(callback.timestamp).minus(Duration.ofMinutes(10))

        val result = service.verifyAndStore(rawQuery, earlier)

        result.newlyStored shouldBe false
        verify(signatureVerifier).verify(callback.signedPayload, callback.signature, callback.keyId)
        verify(repository, never()).saveAndFlush(any<GoogleAdSsvEvent>())
    }
})
