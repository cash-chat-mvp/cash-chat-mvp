package com.wnl.cashchat.api.domain.point.persistence.entity

/**
 * 포인트 적립/차감 사유. 적립 채널(출석·광고)과 소비 채널(상점)을 함께 정의한다.
 */
enum class PointTransactionReason {
    ATTENDANCE,
    AD_REWARD,
    SHOP_PURCHASE,
}
