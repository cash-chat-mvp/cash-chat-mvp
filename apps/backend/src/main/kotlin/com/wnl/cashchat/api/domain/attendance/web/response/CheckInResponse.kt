package com.wnl.cashchat.api.domain.attendance.web.response

import com.wnl.cashchat.api.domain.attendance.service.CheckInResult
import com.wnl.cashchat.api.domain.attendance.service.RewardView

data class BonusItemResponse(
    val itemCode: String,
    val quantity: Int,
)

data class RewardPreviewResponse(
    val dayCount: Int,
    val coin: Long,
    val bonusItems: List<BonusItemResponse>,
) {
    companion object {
        fun from(view: RewardView): RewardPreviewResponse =
            RewardPreviewResponse(
                dayCount = view.dayCount,
                coin = view.coin,
                bonusItems = view.bonusItems.map { BonusItemResponse(it.itemCode, it.quantity) },
            )
    }
}

data class CheckInResponse(
    val awardedCoin: Long,
    val streakDayCount: Int,
    val bonusItems: List<BonusItemResponse>,
    val nextRewardPreview: RewardPreviewResponse,
) {
    companion object {
        fun from(result: CheckInResult): CheckInResponse =
            CheckInResponse(
                awardedCoin = result.awardedCoin,
                streakDayCount = result.streakDayCount,
                bonusItems = result.bonusItems.map { BonusItemResponse(it.itemCode, it.quantity) },
                nextRewardPreview = RewardPreviewResponse.from(result.nextReward),
            )
    }
}
