package com.wnl.cashchat.api.domain.shop.persistence.entity

import com.wnl.cashchat.api.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * 구매 주문. (user_id, idempotency_key) 복합 유니크로 멱등성을 사용자별 격리.
 * snapshotPrice 는 구매 시점 총 결제 코인(priceCoin * qty).
 */
@Entity
@Table(
    name = "purchase_order",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_purchase_order_user_idem", columnNames = ["user_id", "idempotency_key"]),
    ],
)
class PurchaseOrder(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "idempotency_key", nullable = false, length = 255)
    val idempotencyKey: String,

    @Column(name = "item_code", nullable = false, length = 50)
    val itemCode: String,

    @Column(nullable = false)
    val qty: Int,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val status: PurchaseOrderStatus,

    @Column(name = "snapshot_price", nullable = false)
    val snapshotPrice: Long,
) : BaseEntity()
