package com.wnl.cashchat.api.domain.invite.web.response

import com.wnl.cashchat.api.domain.invite.service.RedeemResult

data class RedeemResponse(
    val success: Boolean,
    val awardedEnergy: Int,
    val message: String?,
) {
    companion object {
        // 실패는 예외로 던져 핸들러가 처리하므로, 정상 반환은 항상 success=true.
        fun from(r: RedeemResult) = RedeemResponse(success = true, awardedEnergy = r.awardedEnergy, message = null)
    }
}
