package com.wnl.cashchat.api.domain.offerwall.service

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

/**
 * TnkOfferwallService 의 적립 분기 단위 테스트(Docker 불필요). DB 연동 검증은
 * TnkOfferwallServiceIntegrationTest(TestContainers)가 담당하고, 여기서는 환산 결과에 따른
 * recordTransaction 호출 여부 같은 분기 로직을 mock 으로 빠르게 검증한다.
 */
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
        val callback = TnkOfferwallCallback(seqId = "s9", mdUserNm = "tok", payPnt = 1, rawQuery = "raw")
        whenever(verifier.isValid(any())).thenReturn(true)
        whenever(callbackRepository.findForUpdate("s9")).thenReturn(callback)
        whenever(tokenService.resolveUserId("tok")).thenReturn(7L)

        // payPnt=1, ratio=0.5 → floor(0.5)=0 코인
        val status = service.handleCallback(params("s9", "tok", 1), now)

        status shouldBe TnkOfferwallStatus.GRANTED
        callback.status shouldBe TnkOfferwallStatus.GRANTED
        callback.coinAmount shouldBe 0L
        // 0 코인은 불필요한 0원 트랜잭션을 남기지 않는다.
        verify(userPointService, never()).recordTransaction(any(), any(), any(), any())
    }

    test("callback converting to positive coins records the transaction") {
        build(ratio = 0.5)
        val callback = TnkOfferwallCallback(seqId = "s10", mdUserNm = "tok", payPnt = 10, rawQuery = "raw")
        whenever(verifier.isValid(any())).thenReturn(true)
        whenever(callbackRepository.findForUpdate("s10")).thenReturn(callback)
        whenever(tokenService.resolveUserId("tok")).thenReturn(7L)

        // payPnt=10, ratio=0.5 → floor(5.0)=5 코인
        val status = service.handleCallback(params("s10", "tok", 10), now)

        status shouldBe TnkOfferwallStatus.GRANTED
        callback.coinAmount shouldBe 5L
        verify(userPointService).recordTransaction(eq(7L), eq(5L), eq(PointTransactionReason.OFFERWALL), eq("tnk:offerwall:s10"))
    }
})
