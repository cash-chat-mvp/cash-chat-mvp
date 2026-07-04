package com.wnl.cashchat.api.domain.roulette.web.controller

import com.wnl.cashchat.api.domain.roulette.service.RouletteService
import com.wnl.cashchat.api.domain.roulette.web.request.SpinWithAdRequest
import com.wnl.cashchat.api.domain.roulette.web.response.RouletteIssueNonceResponse
import com.wnl.cashchat.api.domain.roulette.web.response.RouletteSpinResponse
import com.wnl.cashchat.api.domain.roulette.web.response.RouletteStatusResponse
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/roulette")
class RouletteController(
    private val rouletteService: RouletteService,
) {
    @GetMapping("/status")
    fun status(authentication: Authentication): RouletteStatusResponse =
        RouletteStatusResponse.from(rouletteService.statusOf(authentication.userId(), Instant.now()))

    @PostMapping("/spin")
    fun spin(authentication: Authentication): RouletteSpinResponse =
        RouletteSpinResponse.from(rouletteService.spinFree(authentication.userId(), Instant.now()))

    @PostMapping("/issue-nonce")
    fun issueNonce(authentication: Authentication): RouletteIssueNonceResponse =
        RouletteIssueNonceResponse.from(rouletteService.issueNonce(authentication.userId(), Instant.now()))

    @PostMapping("/spin-with-ad")
    fun spinWithAd(
        authentication: Authentication,
        @RequestBody request: SpinWithAdRequest,
    ): RouletteSpinResponse =
        RouletteSpinResponse.from(rouletteService.spinWithAd(authentication.userId(), request.nonce, Instant.now()))

    private fun Authentication.userId(): Long =
        principal as? Long ?: throw AuthenticationCredentialsNotFoundException("Invalid authenticated principal")
}
