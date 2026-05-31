package com.wnl.cashchat.api.domain.ad.web.controller

import com.wnl.cashchat.api.domain.ad.service.AdRewardNonceService
import com.wnl.cashchat.api.domain.ad.web.response.IssueNonceResponse
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/ads/reward")
class AdRewardController(
    private val adRewardNonceService: AdRewardNonceService,
) {
    @PostMapping("/issue-nonce")
    fun issueNonce(authentication: Authentication): IssueNonceResponse =
        IssueNonceResponse.from(
            adRewardNonceService.issueFor(authentication.userId(), Instant.now())
        )

    private fun Authentication.userId(): Long =
        principal as? Long ?: throw AuthenticationCredentialsNotFoundException("Invalid authenticated principal")
}
