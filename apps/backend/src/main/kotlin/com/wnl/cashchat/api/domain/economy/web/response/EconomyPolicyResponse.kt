package com.wnl.cashchat.api.domain.economy.web.response

data class EconomyPolicyResponse(
    val energyCostPerChat: Long,
    val chatRewardPt: Long,
    val evolutionExpPerChat: Long,
    val maxEnergy: Long,
    val rewardedEnergyPerAd: Long,
    val attendanceEnergyReward: Long,
    val energyExpirationNoticeDays: Long,
)
