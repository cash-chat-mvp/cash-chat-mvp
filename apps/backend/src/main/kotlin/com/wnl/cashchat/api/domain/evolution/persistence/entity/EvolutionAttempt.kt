package com.wnl.cashchat.api.domain.evolution.persistence.entity

import com.wnl.cashchat.api.common.entity.BaseEntity
import com.wnl.cashchat.api.domain.evolution.service.TimingGrade
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
 * 진화 시도 원장. idempotencyKey 유니크 제약으로 같은 시도의 중복 처리(이중 차감)를 차단한다.
 */
@Entity
@Table(
    name = "evolution_attempt",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_evolution_attempt_user_key", columnNames = ["user_id", "idempotency_key"])
    ],
    indexes = [
        Index(name = "idx_evolution_attempt_user_id", columnList = "user_id")
    ]
)
class EvolutionAttempt(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "from_level", nullable = false)
    val fromLevel: Int,

    @Column(nullable = false)
    val cost: Long,

    @Column(nullable = false)
    val success: Boolean,

    @Column(name = "result_level", nullable = false)
    val resultLevel: Int,

    @Column(name = "idempotency_key", nullable = false, length = 255)
    val idempotencyKey: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "timing_grade")
    val timingGrade: TimingGrade? = null,

    @Column(name = "timing_bonus_rate")
    val timingBonusRate: Double? = null,

    @Column(name = "base_success_rate")
    val baseSuccessRate: Double? = null,

    @Column(name = "final_success_rate")
    val finalSuccessRate: Double? = null,
) : BaseEntity()