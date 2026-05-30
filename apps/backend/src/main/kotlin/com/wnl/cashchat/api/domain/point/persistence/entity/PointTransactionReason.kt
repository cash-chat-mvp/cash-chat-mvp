package com.wnl.cashchat.api.domain.point.persistence.entity

/**
 * 포인트 적립/차감 사유. Phase 1 적립 채널(출석·광고) 중심으로 정의하며,
 * 소비(상점 등) 사유는 해당 도메인 구현 시 추가한다.
 */
enum class PointTransactionReason {
    ATTENDANCE,
    AD_REWARD,
}
