package com.wnl.cashchat.api.domain.offerwall.web.controller

import com.wnl.cashchat.api.domain.offerwall.persistence.entity.OfferwallPlatform
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
import org.springframework.web.bind.annotation.PathVariable
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

    @PostMapping("/callback/{platform}")
    @Operation(summary = "Handle TNK offerwall server postback", description = "Verifies md_chk with the platform app key, resolves user, credits coins idempotently.")
    fun handleCallback(
        @PathVariable platform: String,
        @RequestParam("seq_id") seqId: String,
        @RequestParam("pay_pnt") payPnt: Long,
        @RequestParam("md_user_nm") mdUserNm: String,
        @RequestParam("md_chk") mdChk: String,
    ): ResponseEntity<String> {
        val resolvedPlatform = OfferwallPlatform.from(platform)
        val rawQuery = "seq_id=$seqId&pay_pnt=$payPnt&md_user_nm=$mdUserNm&md_chk=$mdChk"
        tnkOfferwallService.handleCallback(
            resolvedPlatform,
            TnkOfferwallCallbackParams(seqId = seqId, payPnt = payPnt, mdUserNm = mdUserNm, mdChk = mdChk, rawQuery = rawQuery),
            Instant.now(),
        )
        return ResponseEntity.ok(tnkOfferwallProperties.ack.successBody)
    }

    private fun Authentication.userId(): Long =
        principal as? Long ?: throw AuthenticationCredentialsNotFoundException("Invalid authenticated principal")
}
