package com.wnl.cashchat.api.domain.offerwall.web.controller

import com.wnl.cashchat.api.domain.offerwall.service.OfferwallUserTokenService
import com.wnl.cashchat.api.domain.offerwall.web.response.UserTokenResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/offerwall/tnk")
@Tag(name = "Offerwall", description = "TNK offerwall endpoints")
class OfferwallController(
    private val offerwallUserTokenService: OfferwallUserTokenService,
) {
    @PostMapping("/user-token")
    @Operation(summary = "Issue TNK offerwall user token", description = "Returns a stable opaque token for TNK setUserName (get-or-create).")
    fun issueUserToken(authentication: Authentication): UserTokenResponse =
        UserTokenResponse(offerwallUserTokenService.tokenFor(authentication.userId()))

    private fun Authentication.userId(): Long =
        principal as? Long ?: throw AuthenticationCredentialsNotFoundException("Invalid authenticated principal")
}
