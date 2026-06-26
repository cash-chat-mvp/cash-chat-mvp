package com.wnl.cashchat.api.domain.evolution.web.controller

import com.wnl.cashchat.api.domain.evolution.properties.EvolutionProperties
import com.wnl.cashchat.api.domain.evolution.service.EvolutionService
import com.wnl.cashchat.api.domain.evolution.service.TimingSessionStore
import com.wnl.cashchat.api.domain.evolution.web.request.EvolutionAttemptRequest
import com.wnl.cashchat.api.domain.evolution.web.response.EvolutionAttemptResponse
import com.wnl.cashchat.api.domain.evolution.web.response.EvolutionAttemptsResponse
import com.wnl.cashchat.api.domain.evolution.web.response.EvolutionStateResponse
import com.wnl.cashchat.api.domain.evolution.web.response.TimingSessionResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/evolution")
@Validated
class EvolutionController(
    private val evolutionService: EvolutionService,
    private val timingSessionStore: TimingSessionStore,
    private val timingConfig: EvolutionProperties.TimingConfig,
) {
    @GetMapping("/me")
    fun getState(authentication: Authentication): EvolutionStateResponse =
        EvolutionStateResponse.from(evolutionService.getState(authentication.userId()))

    @PostMapping("/attempt")
    fun attempt(
        authentication: Authentication,
        @Valid @RequestBody request: EvolutionAttemptRequest,
    ): EvolutionAttemptResponse =
        EvolutionAttemptResponse.from(
            evolutionService.attempt(authentication.userId(), request.idempotencyKey)
        )

    @GetMapping("/attempts")
    fun getAttempts(
        authentication: Authentication,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) limit: Int,
    ): EvolutionAttemptsResponse =
        EvolutionAttemptsResponse.from(evolutionService.getAttempts(authentication.userId(), limit))

    @PostMapping("/timing-sessions")
    fun createTimingSession(authentication: Authentication): TimingSessionResponse =
        TimingSessionResponse.from(timingSessionStore.issue(authentication.userId()), timingConfig)

    private fun Authentication.userId(): Long =
        principal as? Long ?: throw AuthenticationCredentialsNotFoundException("Invalid authenticated principal")
}