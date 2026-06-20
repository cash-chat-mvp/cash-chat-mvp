package com.wnl.cashchat.api.domain.evolution.web.controller

import com.wnl.cashchat.api.domain.evolution.service.EvolutionService
import com.wnl.cashchat.api.domain.evolution.web.request.EvolutionAttemptRequest
import com.wnl.cashchat.api.domain.evolution.web.response.EvolutionAttemptResponse
import com.wnl.cashchat.api.domain.evolution.web.response.EvolutionStateResponse
import jakarta.validation.Valid
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/evolution")
class EvolutionController(
    private val evolutionService: EvolutionService,
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

    private fun Authentication.userId(): Long =
        principal as? Long ?: throw AuthenticationCredentialsNotFoundException("Invalid authenticated principal")
}