package com.nomadclub.cashchat.shared.invite

/** 친구 초대 상태(서버가 진실, 스텁이 모사). 금액·한도는 서버 설정값. */
data class InviteStatus(
    val myCode: String,
    val invitedCount: Int,
    val redeemAvailable: Boolean,
    val rewardCoin: Int,
    val rewardEnergy: Int,
)

/** 추천 코드 입력 결과. */
data class RedeemResult(val success: Boolean, val awardedEnergy: Int, val message: String?)
