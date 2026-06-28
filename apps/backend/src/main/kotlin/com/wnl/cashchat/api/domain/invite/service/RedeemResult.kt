package com.wnl.cashchat.api.domain.invite.service

import com.wnl.cashchat.api.domain.invite.persistence.entity.InviteRedemptionStatus

data class RedeemResult(
    val awardedEnergy: Int,
    val status: InviteRedemptionStatus,
)
