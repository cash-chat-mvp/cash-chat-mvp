package com.wnl.cashchat.api.domain.energy.web.controller

import com.wnl.cashchat.api.domain.energy.service.EnergyService
import com.wnl.cashchat.api.domain.energy.web.response.EnergyResponse
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/energy")
class EnergyController(
    private val energyService: EnergyService,
) {
    @GetMapping("/me")
    fun getEnergy(authentication: Authentication): EnergyResponse =
        EnergyResponse.from(energyService.getEnergy(authentication.userId()))

    private fun Authentication.userId(): Long =
        principal as? Long ?: throw AuthenticationCredentialsNotFoundException("Invalid authenticated principal")
}
