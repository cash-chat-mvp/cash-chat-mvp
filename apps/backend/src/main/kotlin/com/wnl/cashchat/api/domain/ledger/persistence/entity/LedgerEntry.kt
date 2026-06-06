package com.wnl.cashchat.api.domain.ledger.persistence.entity

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
 * 수익 분배 감사 원장. 각 외부 수익 이벤트의 분배 결과를 불변 기록으로 보관한다.
 * idempotencyKey 유니크 제약으로 같은 이벤트의 중복 분배를 차단한다.
 */
@Entity
@Table(
    name = "ledger_entry",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_ledger_entry_user_key", columnNames = ["user_id", "idempotency_key"])
    ],
    indexes = [
        Index(name = "idx_ledger_entry_user_id", columnList = "user_id")
    ]
)
class LedgerEntry(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val source: RevenueSource,

    @Column(name = "gross_revenue", nullable = false)
    val grossRevenue: Long,

    @Column(name = "risk_reserve", nullable = false)
    val riskReserve: Long,

    @Column(name = "service_reserve", nullable = false)
    val serviceReserve: Long,

    @Column(name = "company_profit", nullable = false)
    val companyProfit: Long,

    @Column(name = "cashable_pt_awarded", nullable = false)
    val cashablePtAwarded: Long,

    @Column(name = "energy_awarded", nullable = false)
    val energyAwarded: Int,

    @Column(name = "idempotency_key", nullable = false, length = 255)
    val idempotencyKey: String,
) : BaseEntity()
