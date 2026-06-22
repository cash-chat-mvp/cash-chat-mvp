package com.wnl.cashchat.api.domain.economy.properties

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.math.BigDecimal

@Validated
@ConfigurationProperties(prefix = "app.economy")
data class EconomyProperties(
    @field:Positive val maxEnergy: Long = 50,
    @field:Positive val energyCostPerChat: Long = 1,
    @field:PositiveOrZero val chatRewardPt: Long = 1,
    @field:PositiveOrZero val evolutionExpPerChat: Long = 1,
    @field:Positive val rewardedEnergyPerAd: Long = 3,
    @field:Positive val attendanceEnergyReward: Long = 4,
    @field:Positive val adEnergyExpirationDays: Long = 30,
    @field:Positive val attendanceEnergyExpirationDays: Long = 7,
    @field:Positive val energyExpirationNoticeDays: Long = 3,
    val rewardChatEnabled: Boolean = true,
    val rewardedAdEnabled: Boolean = true,
    val attendanceRewardEnabled: Boolean = true,
    val evolutionEnabled: Boolean = true,
    val cashoutEnabled: Boolean = true,
    val premiumRoutingEnabled: Boolean = true,
    @field:DecimalMin("0.0") val sharedPoolMarginPerChat: BigDecimal = BigDecimal.ZERO,
    @field:DecimalMin("0.0") val premiumDeltaPt: BigDecimal = BigDecimal.ZERO,
    val nanoModelName: String = "",
    val premiumModelName: String = "",
)
