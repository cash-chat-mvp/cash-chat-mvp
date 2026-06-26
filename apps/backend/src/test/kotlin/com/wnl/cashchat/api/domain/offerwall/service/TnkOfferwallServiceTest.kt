package com.wnl.cashchat.api.domain.offerwall.service

import com.wnl.cashchat.api.domain.offerwall.persistence.entity.OfferwallPlatform
import com.wnl.cashchat.api.domain.offerwall.persistence.entity.TnkOfferwallCallback
import com.wnl.cashchat.api.domain.offerwall.persistence.entity.TnkOfferwallStatus
import com.wnl.cashchat.api.domain.offerwall.persistence.repository.TnkOfferwallCallbackRepository
import com.wnl.cashchat.api.domain.offerwall.properties.TnkOfferwallProperties
import com.wnl.cashchat.api.domain.point.persistence.entity.PointTransactionReason
import com.wnl.cashchat.api.domain.point.service.UserPointService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class TnkOfferwallServiceTest : FunSpec({
    lateinit var callbackRepository: TnkOfferwallCallbackRepository
    lateinit var verifier: TnkMdChecksumVerifier
    lateinit var tokenService: OfferwallUserTokenService
    lateinit var userPointService: UserPointService
    lateinit var service: TnkOfferwallService

    val now = Instant.parse("2026-06-17T00:00:00Z")

    fun params(seqId: String, token: String, payPnt: Long) =
        TnkOfferwallCallbackParams(seqId = seqId, payPnt = payPnt, mdUserNm = token, mdChk = "chk", rawQuery = "raw")

    fun build(ratio: Double) {
        callbackRepository = mock()
        verifier = mock()
        tokenService = mock()
        userPointService = mock()
        service = TnkOfferwallService(
            callbackRepository, verifier, tokenService, userPointService,
            TnkOfferwallProperties(pointToCoinRatio = ratio),
        )
    }

    test("callback converting to zero coins grants without recording a point transaction") {
        build(ratio = 0.5)
        val callback = TnkOfferwallCallback(platform = OfferwallPlatform.ANDROID, seqId = "s9", mdUserNm = "tok", payPnt = 1, rawQuery = "raw")
        whenever(verifier.isValid(any(), any())).thenReturn(true)
        whenever(callbackRepository.findForUpdate(OfferwallPlatform.ANDROID, "s9")).thenReturn(callback)
        whenever(tokenService.resolveUserId("tok")).thenReturn(7L)

        val status = service.handleCallback(OfferwallPlatform.ANDROID, params("s9", "tok", 1), now)

        status shouldBe TnkOfferwallStatus.GRANTED
        callback.status shouldBe TnkOfferwallStatus.GRANTED
        callback.coinAmount shouldBe 0L
        verify(userPointService, never()).recordTransaction(any(), any(), any(), any())
    }

    test("callback converting to positive coins records the transaction with platform-scoped idempotency key") {
        build(ratio = 0.5)
        val callback = TnkOfferwallCallback(platform = OfferwallPlatform.ANDROID, seqId = "s10", mdUserNm = "tok", payPnt = 10, rawQuery = "raw")
        whenever(verifier.isValid(any(), any())).thenReturn(true)
        whenever(callbackRepository.findForUpdate(OfferwallPlatform.ANDROID, "s10")).thenReturn(callback)
        whenever(tokenService.resolveUserId("tok")).thenReturn(7L)

        val status = service.handleCallback(OfferwallPlatform.ANDROID, params("s10", "tok", 10), now)

        status shouldBe TnkOfferwallStatus.GRANTED
        callback.coinAmount shouldBe 5L
        verify(userPointService).recordTransaction(eq(7L), eq(5L), eq(PointTransactionReason.OFFERWALL), eq("tnk:offerwall:android:s10"))
    }
})
