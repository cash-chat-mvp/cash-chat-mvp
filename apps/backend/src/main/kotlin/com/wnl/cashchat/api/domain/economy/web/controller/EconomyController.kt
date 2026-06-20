package com.wnl.cashchat.api.domain.economy.web.controller

import com.wnl.cashchat.api.domain.economy.properties.EconomyProperties
import com.wnl.cashchat.api.domain.economy.service.WalletService
import com.wnl.cashchat.api.domain.economy.web.response.EconomyPolicyResponse
import com.wnl.cashchat.api.domain.economy.web.response.EconomySnapshotResponse
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/v1/economy")
class EconomyController(
    private val walletService: WalletService,
    private val economyProperties: EconomyProperties,
) {
    @GetMapping("/me")
    fun me(authentication: Authentication): EconomySnapshotResponse {
        val w = walletService.snapshot(authentication.userId())
        return EconomySnapshotResponse(
            serverTime = Instant.now(),
            energy = EconomySnapshotResponse.EnergyView(w.energyAvailable, w.energyReserved, economyProperties.maxEnergy),
            point = EconomySnapshotResponse.PointView(w.pendingCashablePt, w.confirmedCashablePt),
            evolution = EconomySnapshotResponse.EvolutionView(w.evolutionLevel, w.evolutionExp, w.evolutionFailStack),
            features = EconomySnapshotResponse.FeaturesView(
                rewardChatEnabled = economyProperties.rewardChatEnabled,
                rewardedAdEnabled = economyProperties.rewardedAdEnabled,
                attendanceRewardEnabled = economyProperties.attendanceRewardEnabled,
                evolutionEnabled = economyProperties.evolutionEnabled,
                cashoutEnabled = economyProperties.cashoutEnabled,
            ),
        )
    }

    @GetMapping("/policy")
    fun policy(): EconomyPolicyResponse = EconomyPolicyResponse(
        energyCostPerChat = economyProperties.energyCostPerChat,
        chatRewardPt = economyProperties.chatRewardPt,
        evolutionExpPerChat = economyProperties.evolutionExpPerChat,
        maxEnergy = economyProperties.maxEnergy,
        rewardedEnergyPerAd = economyProperties.rewardedEnergyPerAd,
        attendanceEnergyReward = economyProperties.attendanceEnergyReward,
        energyExpirationNoticeDays = economyProperties.energyExpirationNoticeDays,
    )

    private fun Authentication.userId(): Long =
        principal as? Long ?: throw IllegalArgumentException("Invalid authenticated principal")
}
