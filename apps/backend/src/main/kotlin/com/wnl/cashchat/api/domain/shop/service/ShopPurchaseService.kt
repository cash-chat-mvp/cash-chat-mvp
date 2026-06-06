package com.wnl.cashchat.api.domain.shop.service

import com.wnl.cashchat.api.domain.inventory.persistence.repository.UserInventoryRepository
import com.wnl.cashchat.api.domain.inventory.service.InventoryLine
import com.wnl.cashchat.api.domain.point.exception.InsufficientPointsException
import com.wnl.cashchat.api.domain.point.persistence.entity.PointTransactionReason
import com.wnl.cashchat.api.domain.point.persistence.repository.UserPointRepository
import com.wnl.cashchat.api.domain.point.service.UserPointService
import com.wnl.cashchat.api.domain.shop.exception.IdempotencyKeyConflictException
import com.wnl.cashchat.api.domain.shop.exception.InsufficientCoinException
import com.wnl.cashchat.api.domain.shop.exception.ItemInactiveException
import com.wnl.cashchat.api.domain.shop.exception.ItemNotFoundException
import com.wnl.cashchat.api.domain.shop.persistence.entity.PurchaseOrder
import com.wnl.cashchat.api.domain.shop.persistence.entity.PurchaseOrderStatus
import com.wnl.cashchat.api.domain.shop.persistence.repository.PurchaseOrderRepository
import com.wnl.cashchat.api.domain.shop.persistence.repository.ShopItemGrantRepository
import com.wnl.cashchat.api.domain.shop.persistence.repository.ShopItemRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 구매 트랜잭션 코어. 전역 락 순서: point(FOR UPDATE) → user_inventory(UPSERT) → purchase_order(INSERT).
 *
 * 동시 INSERT 경합(같은 (userId, key) 더블클릭)은 purchase_order 복합 유니크 위반(DataIntegrityViolationException)으로
 * 패자가 잡히지만, 그 트랜잭션은 rollback-only 로 마킹돼 내부 복구가 불가능하다.
 * 따라서 경합 복구(재조회 후 멱등 처리)는 트랜잭션 경계 바깥의 ShopPurchaseFacade 가 수행한다.
 */
@Service
class ShopPurchaseService(
    private val shopItemRepository: ShopItemRepository,
    private val shopItemGrantRepository: ShopItemGrantRepository,
    private val purchaseOrderRepository: PurchaseOrderRepository,
    private val userInventoryRepository: UserInventoryRepository,
    private val userPointService: UserPointService,
    private val userPointRepository: UserPointRepository,
) {
    @Transactional
    fun purchase(userId: Long, command: PurchaseCommand): PurchaseResult {
        require(command.qty >= 1) { "qty must be >= 1, got ${command.qty}" }

        // 1) 멱등성 선조회: 같은 (userId, key) 주문이 이미 있으면 재차감 없이 현재 상태 반환
        purchaseOrderRepository.findByUserIdAndIdempotencyKey(userId, command.idempotencyKey)?.let {
            return buildReplayResult(userId, it, command)
        }

        // 2) 아이템 검증
        val item = shopItemRepository.findById(command.itemCode)
            .orElseThrow { ItemNotFoundException() }
        if (!item.isActive) throw ItemInactiveException()

        val totalPrice = item.priceCoin * command.qty

        // 3) 코인 차감(point 행 FOR UPDATE → 잔액 검증 → 멱등 원장). 402(부족) → 상점 도메인 400 으로 변환.
        try {
            userPointService.recordTransaction(
                userId = userId,
                delta = -totalPrice,
                reason = PointTransactionReason.SHOP_PURCHASE,
                idempotencyKey = "shop:purchase:$userId:${command.idempotencyKey}",
            )
        } catch (e: InsufficientPointsException) {
            throw InsufficientCoinException()
        }

        // 4) 인벤토리 적재: grant 를 grantItemCode 오름차순으로 UPSERT(락 순서 고정 → 데드락 방지)
        val grants = shopItemGrantRepository.findByItemCodeOrderByGrantItemCodeAsc(command.itemCode)
        grants.forEach { grant ->
            userInventoryRepository.upsertQty(userId, grant.grantItemCode, grant.grantQty * command.qty)
        }

        // 5) 주문 INSERT(마지막). saveAndFlush 로 DIVE 를 트랜잭션 안에서 강제 → 경합 패자는 전체 롤백.
        val order = purchaseOrderRepository.saveAndFlush(
            PurchaseOrder(
                userId = userId,
                idempotencyKey = command.idempotencyKey,
                itemCode = command.itemCode,
                qty = command.qty,
                status = PurchaseOrderStatus.COMPLETED,
                snapshotPrice = totalPrice,
            )
        )

        return buildResult(userId, order)
    }

    /**
     * 동시 INSERT 경합 패자 복구: 원 트랜잭션은 이미 롤백됨. Facade(비트랜잭션)가 호출하므로
     * 이 메서드의 @Transactional 이 신규 트랜잭션을 열어 커밋된 주문을 재조회한다.
     * 주문이 없으면(= purchase_order 유니크 외의 무결성 위반) null 을 반환해 Facade 가 원 예외를 전파한다.
     */
    @Transactional
    fun replayAfterRace(userId: Long, command: PurchaseCommand): PurchaseResult? {
        val order = purchaseOrderRepository.findByUserIdAndIdempotencyKey(userId, command.idempotencyKey)
            ?: return null
        return buildReplayResult(userId, order, command)
    }

    // 저장된 주문의 itemCode/qty 가 요청과 일치하는지 검증(불일치 → 409), 일치하면 현재 상태 반환.
    private fun buildReplayResult(userId: Long, order: PurchaseOrder, command: PurchaseCommand): PurchaseResult {
        if (order.itemCode != command.itemCode || order.qty != command.qty) {
            throw IdempotencyKeyConflictException()
        }
        return buildResult(userId, order)
    }

    // 현재 시점 코인 잔액 + 인벤토리(itemCode 오름차순)를 재조회해 결과를 만든다(stale 스냅샷 미반환).
    private fun buildResult(userId: Long, order: PurchaseOrder): PurchaseResult {
        val balance = userPointRepository.findByUserId(userId)?.balance ?: 0L
        val inventory = userInventoryRepository.findByUserIdOrderByItemCodeAsc(userId)
            .map { InventoryLine(itemCode = it.itemCode, qty = it.qty) }
        return PurchaseResult(
            purchaseOrderId = order.id,
            status = order.status,
            coinBalance = balance,
            inventory = inventory,
        )
    }
}
