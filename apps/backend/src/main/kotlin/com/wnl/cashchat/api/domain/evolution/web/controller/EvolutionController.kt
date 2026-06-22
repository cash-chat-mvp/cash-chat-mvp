package com.wnl.cashchat.api.domain.evolution.web.controller

import com.wnl.cashchat.api.domain.evolution.exception.EvolutionIdempotencyKeyRequiredException
import com.wnl.cashchat.api.domain.evolution.service.EvolutionService
import com.wnl.cashchat.api.domain.evolution.web.request.EvolutionAttemptRequest
import com.wnl.cashchat.api.domain.evolution.web.response.EvolutionAttemptResponse
import com.wnl.cashchat.api.domain.evolution.web.response.EvolutionMeResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/evolution")
class EvolutionController(
    private val evolutionService: EvolutionService,
) {
    @GetMapping("/me")
    fun me(authentication: Authentication): EvolutionMeResponse {
        val m = evolutionService.me(authentication.userId())
        return EvolutionMeResponse(
            level = m.level, exp = m.exp, failStack = m.failStack, maxLevel = m.maxLevel,
            requiredExp = m.requiredExp, baseSuccessRate = m.baseSuccessRate,
            finalSuccessRate = m.finalSuccessRate, canAttempt = m.canAttempt,
        )
    }

    @PostMapping("/attempts")
    @ResponseStatus(HttpStatus.CREATED)
    fun attempt(
        authentication: Authentication,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        @RequestBody request: EvolutionAttemptRequest,
    ): EvolutionAttemptResponse {
        val key = idempotencyKey?.takeIf { it.isNotBlank() } ?: throw EvolutionIdempotencyKeyRequiredException()
        val attempt = evolutionService.attempt(authentication.userId(), key, request.expectedLevel)
        return EvolutionAttemptResponse.from(attempt)
    }

    @GetMapping("/attempts/{id}")
    fun attemptById(authentication: Authentication, @PathVariable id: Long): EvolutionAttemptResponse =
        EvolutionAttemptResponse.from(evolutionService.findAttempt(authentication.userId(), id))

    private fun Authentication.userId(): Long =
        principal as? Long ?: throw IllegalArgumentException("Invalid authenticated principal")
}
