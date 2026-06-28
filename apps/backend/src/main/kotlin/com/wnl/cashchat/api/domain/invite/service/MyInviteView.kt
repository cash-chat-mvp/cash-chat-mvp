package com.wnl.cashchat.api.domain.invite.service

data class MyInviteView(
    val myCode: String,
    val invitedCount: Long,
    val redeemAvailable: Boolean,
    val rewardCoin: Long,
    val rewardEnergy: Int,
)
