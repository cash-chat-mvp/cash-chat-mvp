package com.wnl.cashchat.api.domain.shop.persistence.entity

/**
 * 구매 주문 상태.
 * - COMPLETED: 트랜잭션 커밋 성공. Phase 1 에서 purchase_order 행이 가지는 유일한 값.
 * - FAILED: 예약값(사후 보상 트랜잭션 실패 마킹용). Phase 1 미사용. 모니터링은 이 값=0 을 정상으로 가정.
 */
enum class PurchaseOrderStatus {
    COMPLETED,
    FAILED,
}
