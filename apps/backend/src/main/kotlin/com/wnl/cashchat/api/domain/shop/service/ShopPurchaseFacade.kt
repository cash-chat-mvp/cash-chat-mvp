package com.wnl.cashchat.api.domain.shop.service

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service

/**
 * 구매 진입점. @Transactional 을 두지 않는다 — 동시 INSERT 경합 시 ShopPurchaseService.purchase 의
 * 트랜잭션은 rollback-only 로 마킹되므로, 복구(재조회)는 반드시 그 트랜잭션 바깥에서 신규 트랜잭션으로 해야 한다.
 */
@Service
class ShopPurchaseFacade(
    private val shopPurchaseService: ShopPurchaseService,
) {
    fun purchase(userId: Long, command: PurchaseCommand): PurchaseResult =
        try {
            shopPurchaseService.purchase(userId, command)
        } catch (e: DataIntegrityViolationException) {
            // 경합 패자: 커밋된 주문을 신규 트랜잭션으로 재조회해 멱등 처리.
            // purchase_order 유니크 위반이면 주문 존재 → 현재 상태/409. 그 외 무결성 위반이면 null → 원 예외 전파.
            shopPurchaseService.replayAfterRace(userId, command) ?: throw e
        }
}
