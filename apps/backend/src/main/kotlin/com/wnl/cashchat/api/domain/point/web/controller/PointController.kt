package com.wnl.cashchat.api.domain.point.web.controller

import com.wnl.cashchat.api.common.security.userId
import com.wnl.cashchat.api.domain.point.service.UserPointService
import com.wnl.cashchat.api.domain.point.web.response.PointBalanceResponse
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/points")
class PointController(
    private val userPointService: UserPointService,
) {
    @GetMapping("/me")
    fun getMyBalance(authentication: Authentication): PointBalanceResponse =
        PointBalanceResponse(userPointService.getBalance(authentication.userId()))
}
