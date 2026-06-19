package com.wnl.cashchat.api.domain.offerwall.web.controller

import com.wnl.cashchat.api.domain.offerwall.properties.TnkOfferwallProperties
import com.wnl.cashchat.api.domain.offerwall.service.OfferwallUserTokenService
import com.wnl.cashchat.api.domain.offerwall.service.TnkOfferwallCallbackParams
import com.wnl.cashchat.api.domain.offerwall.service.TnkOfferwallService
import com.wnl.cashchat.api.domain.offerwall.web.response.UserTokenResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/offerwall/tnk")
@Tag(name = "Offerwall", description = "TNK offerwall endpoints")
class OfferwallController(
    private val offerwallUserTokenService: OfferwallUserTokenService,
    private val tnkOfferwallService: TnkOfferwallService,
    private val tnkOfferwallProperties: TnkOfferwallProperties,
) {
    @PostMapping("/user-token")
    @Operation(summary = "Issue TNK offerwall user token", description = "Returns a stable opaque token for TNK setUserName (get-or-create).")
    fun issueUserToken(authentication: Authentication): UserTokenResponse =
        UserTokenResponse(offerwallUserTokenService.tokenFor(authentication.userId()))

    @PostMapping("/callback")
    @Operation(summary = "Handle TNK offerwall server postback", description = "Verifies md_chk, resolves user, credits coins idempotently, records ledger.")
    fun handleCallback(
        @RequestParam("seq_id") seqId: String,
        @RequestParam("pay_pnt") payPnt: Long,
        @RequestParam("md_user_nm") mdUserNm: String,
        @RequestParam("md_chk") mdChk: String,
    ): ResponseEntity<String> {
        // 원장 기록용 원본 표현(파라미터 재구성). 정확한 전송 방식/ack 규격은 TNK 확인 후 확정(spec 검증 TODO).
        val rawQuery = "seq_id=$seqId&pay_pnt=$payPnt&md_user_nm=$mdUserNm&md_chk=$mdChk"
        tnkOfferwallService.handleCallback(
            TnkOfferwallCallbackParams(seqId = seqId, payPnt = payPnt, mdUserNm = mdUserNm, mdChk = mdChk, rawQuery = rawQuery),
            Instant.now(),
        )
        // 처리된 콜백(적립·거절·중복)에는 성공 ack 를 반환해 재전송 폭주를 막는다. 미처리 예외는 500 으로 재시도 유도.
        return ResponseEntity.ok(tnkOfferwallProperties.ack.successBody)
    }

    private fun Authentication.userId(): Long =
        principal as? Long ?: throw AuthenticationCredentialsNotFoundException("Invalid authenticated principal")
}
