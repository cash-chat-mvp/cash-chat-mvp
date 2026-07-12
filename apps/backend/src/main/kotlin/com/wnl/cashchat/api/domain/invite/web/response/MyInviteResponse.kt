package com.wnl.cashchat.api.domain.invite.web.response

import com.wnl.cashchat.api.domain.invite.service.MyInviteView

data class MyInviteResponse(
    val myCode: String,
    val invitedCount: Long,
    val redeemAvailable: Boolean,
    val rewardCoin: Long,
    val rewardEnergy: Int,
) {
    companion object {
        fun from(v: MyInviteView) = MyInviteResponse(
            myCode = v.myCode,
            invitedCount = v.invitedCount,
            redeemAvailable = v.redeemAvailable,
            rewardCoin = v.rewardCoin,
            rewardEnergy = v.rewardEnergy,
        )
    }
}
