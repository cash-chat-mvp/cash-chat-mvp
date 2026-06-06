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
            // 동시 INSERT 경합(같은 userId + idempotencyKey) 시 두 유니크 제약 중 하나가 먼저 위반될 수 있다:
            //   - point_transaction 멱등성 키 유니크(uq_point_transaction_idempotency_key): 보통 이쪽이 먼저 터진다.
            //     recordTransaction 의 findByUserIdForUpdate(SELECT FOR UPDATE)로 직렬화되지만, REPEATABLE READ
            //     (MySQL 기본) 스냅샷이 트랜잭션 첫 읽기(step1 선조회) 시점에 고정되므로, 패자의 비잠금
            //     findByIdempotencyKey 는 승자가 "이미 커밋한" 원장 행을 스냅샷에서 보지 못해 INSERT 를 시도 → 위반.
            //   - purchase_order 복합 유니크(uk_purchase_order_user_idem): 패자가 위 단계를 통과한 경우 주문 INSERT 단계 경합.
            // 두 경우 모두 "같은 키의 구매가 이미 진행 중이거나 완료됐음"을 의미하므로 멱등 복구를 시도한다.
            // 그 외 무결성 위반(FK 등)은 가리지 않고 그대로 전파한다(AttendanceService 패턴과 동일).
            if (!isPurchaseRaceViolation(e)) throw e
            // 경합 패자: 커밋된 주문을 신규 트랜잭션으로 재조회해 멱등 처리(일치 → 현재 상태, 불일치 → 409).
            shopPurchaseService.replayAfterRace(userId, command) ?: throw e
        }

    /**
     * 예외 원인 체인의 메시지에 구매 경합과 관련된 유니크 제약명이 포함되는지 검사한다.
     * (H2 MySQL 모드·MySQL 8 모두 제약/인덱스명이 메시지에 노출됨 — AttendanceService 와 동일 접근)
     *
     * 동시 구매 경합은 두 제약 중 하나를 먼저 위반할 수 있다:
     * 1) [uq_point_transaction_idempotency_key] — recordTransaction INSERT 단계(REPEATABLE READ 스냅샷 가시성 경합)
     * 2) [uk_purchase_order_user_idem] — purchase_order INSERT 단계
     */
    private fun isPurchaseRaceViolation(e: DataIntegrityViolationException): Boolean =
        generateSequence(e as Throwable) { it.cause }
            .any { cause ->
                val msg = cause.message ?: return@any false
                PURCHASE_RACE_CONSTRAINTS.any { msg.contains(it, ignoreCase = true) }
            }

    private companion object {
        private val PURCHASE_RACE_CONSTRAINTS = listOf(
            "uk_purchase_order_user_idem",
            "uq_point_transaction_idempotency_key",
        )
    }
}
