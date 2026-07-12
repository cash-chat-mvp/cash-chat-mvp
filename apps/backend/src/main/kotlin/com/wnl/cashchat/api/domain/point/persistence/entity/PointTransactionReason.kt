package com.wnl.cashchat.api.domain.point.persistence.entity

/**
 * 포인트 적립/차감 사유. 적립 채널(출석·광고·정산)과 소비 채널(상점·진화)을 함께 정의한다.
 */
enum class PointTransactionReason {
    ATTENDANCE,
    AD_REWARD,
    OFFERWALL,
    EVOLUTION_ATTEMPT,
    LEDGER_REWARD,
    SHOP_PURCHASE,
    REFERRAL,
    CHAT_REWARD,
}
