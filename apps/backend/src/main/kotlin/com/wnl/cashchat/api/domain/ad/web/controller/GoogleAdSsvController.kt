package com.wnl.cashchat.api.domain.ad.web.controller

import com.wnl.cashchat.api.common.web.response.ErrorResponse
import com.wnl.cashchat.api.domain.ad.service.AdRewardService
import com.wnl.cashchat.api.domain.ad.service.GoogleAdSsvService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/ads")
@Tag(name = "Ads", description = "Advertising callback endpoints")
class GoogleAdSsvController(
    private val googleAdSsvService: GoogleAdSsvService,
    private val adRewardService: AdRewardService,
) {
    @GetMapping("/google/ssv")
    @Operation(
        summary = "Verify Google AdMob SSV callback",
        description = "Verifies and stores a Google AdMob rewarded ad server-side verification callback."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Callback verified and accepted."),
            ApiResponse(
                responseCode = "400",
                description = "Callback is malformed or signature verification failed.",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "503",
                description = "Google public keys are temporarily unavailable.",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            )
        ]
    )
    fun verify(request: HttpServletRequest): ResponseEntity<Void> {
        val result = googleAdSsvService.verifyAndStore(request.queryString)
        if (result.newlyStored) {
            adRewardService.grantFromCallback(result.callback, Instant.now())
        }
        return ResponseEntity.ok().build()
    }
}
