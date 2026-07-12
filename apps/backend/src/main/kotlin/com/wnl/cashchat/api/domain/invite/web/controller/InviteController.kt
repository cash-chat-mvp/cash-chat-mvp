package com.wnl.cashchat.api.domain.invite.web.controller

import com.wnl.cashchat.api.domain.invite.service.InviteService
import com.wnl.cashchat.api.domain.invite.web.request.RedeemRequest
import com.wnl.cashchat.api.domain.invite.web.response.MyInviteResponse
import com.wnl.cashchat.api.domain.invite.web.response.RedeemResponse
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/invite")
class InviteController(
    private val inviteService: InviteService,
) {
    @GetMapping("/me")
    fun me(authentication: Authentication): MyInviteResponse =
        MyInviteResponse.from(inviteService.getMyInvite(authentication.userId(), Instant.now()))

    @PostMapping("/redeem")
    fun redeem(authentication: Authentication, @RequestBody request: RedeemRequest): RedeemResponse =
        RedeemResponse.from(inviteService.redeem(authentication.userId(), request.code, Instant.now()))

    private fun Authentication.userId(): Long =
        principal as? Long ?: throw AuthenticationCredentialsNotFoundException("Invalid authenticated principal")
}
