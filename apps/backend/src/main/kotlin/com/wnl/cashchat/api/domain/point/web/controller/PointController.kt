package com.wnl.cashchat.api.domain.point.web.controller

import com.wnl.cashchat.api.common.security.userId
import com.wnl.cashchat.api.domain.point.service.UserPointService
import com.wnl.cashchat.api.domain.point.web.response.PointBalanceResponse
import com.wnl.cashchat.api.domain.point.web.response.PointHistoryResponse
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/points")
class PointController(
    private val userPointService: UserPointService,
) {
    @GetMapping("/me")
    fun getMyBalance(authentication: Authentication): PointBalanceResponse =
        PointBalanceResponse(userPointService.getBalance(authentication.userId()))

    @GetMapping("/history")
    fun getMyHistory(
        authentication: Authentication,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PointHistoryResponse {
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, MAX_PAGE_SIZE)
        val pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "id"))
        return PointHistoryResponse.from(userPointService.getHistory(authentication.userId(), pageable))
    }

    private companion object {
        private const val MAX_PAGE_SIZE = 100
    }
}
