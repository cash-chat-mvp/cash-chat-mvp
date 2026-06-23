package com.wnl.cashchat.api.domain.invite.properties

import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "app.invite")
data class InviteProperties(
    @field:Positive val codeLength: Int = 6,
    @field:PositiveOrZero val inviterRewardCoin: Long = 500,
    @field:PositiveOrZero val inviteeRewardEnergy: Int = 10,
    @field:Positive val redeemWindowDays: Int = 7,
    @field:Positive val inviterCap: Int = 20,
)
