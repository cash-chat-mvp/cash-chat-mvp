package com.wnl.cashchat.api.domain.invite.persistence.entity

/** redeem 결과 상태. GRANTED=초대자 코인까지 지급, GRANTED_INVITER_CAPPED=초대자 상한 초과로 코인 미지급(가입자 에너지는 지급). */
enum class InviteRedemptionStatus {
    GRANTED,
    GRANTED_INVITER_CAPPED,
}
