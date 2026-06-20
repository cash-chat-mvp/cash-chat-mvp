package com.wnl.cashchat.api.domain.point.persistence.entity

import com.wnl.cashchat.api.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * 포인트 적립/차감 원장(ledger). idempotencyKey 유니크 제약으로 중복 적립을 차단한다.
 */
@Entity
@Table(
    name = "point_transaction",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_point_transaction_idempotency_key", columnNames = ["idempotency_key"])
    ],
    indexes = [
        Index(name = "idx_point_transaction_user_id", columnList = "user_id")
    ]
)
class PointTransaction(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(nullable = false)
    val delta: Long,

    @Column(name = "balance_after", nullable = false)
    val balanceAfter: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    val reason: PointTransactionReason,

    @Column(name = "idempotency_key", nullable = false, length = 255)
    val idempotencyKey: String,
) : BaseEntity()
