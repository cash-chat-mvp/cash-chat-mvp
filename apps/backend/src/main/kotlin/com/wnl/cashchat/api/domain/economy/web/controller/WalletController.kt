package com.wnl.cashchat.api.domain.economy.web.controller

import com.wnl.cashchat.api.domain.economy.properties.EconomyProperties
import com.wnl.cashchat.api.domain.economy.service.WalletService
import com.wnl.cashchat.api.domain.economy.web.response.WalletResponse
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/wallet")
class WalletController(
    private val walletService: WalletService,
    private val economyProperties: EconomyProperties,
) {
    @GetMapping
    fun wallet(authentication: Authentication): WalletResponse {
        val w = walletService.snapshot(authentication.principal as Long)
        return WalletResponse(
            energyAvailable = w.energyAvailable,
            energyReserved = w.energyReserved,
            maxEnergy = economyProperties.maxEnergy,
            pendingCashablePt = w.pendingCashablePt,
            confirmedCashablePt = w.confirmedCashablePt,
            evolutionExp = w.evolutionExp,
        )
    }
}
