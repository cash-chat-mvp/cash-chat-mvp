package com.wnl.cashchat.api.domain.ad.web.controller

import com.wnl.cashchat.api.common.web.response.ErrorResponse
import com.wnl.cashchat.api.domain.ad.service.AdSsvRewardRouter
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
    private val adSsvRewardRouter: AdSsvRewardRouter,
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
        // 검증과 적립이 동일한 '현재 시각'을 보도록 한 번만 만들어 두 호출에 전달한다.
        val now = Instant.now()
        val result = googleAdSsvService.verifyAndStore(request.queryString, now)
        // 적립 대상 콜백만 grantFromCallback 을 호출한다(ad_unit 불일치·timestamp 윈도우 밖은 미저장이라 건너뜀 →
        // 무의미한 행 락 조회 회피). grantFromCallback 은 이미 GRANTED 된 이벤트를 멱등하게 건너뛴다.
        if (result.eligibleForGranting) {
            adSsvRewardRouter.route(result.callback, now)
        }
        return ResponseEntity.ok().build()
    }
}
