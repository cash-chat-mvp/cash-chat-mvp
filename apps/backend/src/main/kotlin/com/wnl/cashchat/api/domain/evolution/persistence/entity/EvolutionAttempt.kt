package com.wnl.cashchat.api.domain.evolution.persistence.entity

import com.wnl.cashchat.api.common.entity.BaseEntity
import jakarta.persistence.*

@Entity
@Table(
    name = "evolution_attempt",
    uniqueConstraints = [UniqueConstraint(
        name = "uq_evolution_attempt_user_key",
        columnNames = ["user_id", "attempt_key"],
    )],
)
class EvolutionAttempt(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
    @Column(name = "user_id", nullable = false) val userId: Long,
    @Column(name = "attempt_key", nullable = false) val attemptKey: String,
    @Column(name = "level_before", nullable = false) val levelBefore: Int,
    @Column(name = "level_after", nullable = false) val levelAfter: Int,
    @Column(name = "required_exp", nullable = false) val requiredExp: Long,
    @Column(name = "base_success_rate", nullable = false) val baseSuccessRate: Double,
    @Column(name = "fail_stack_before", nullable = false) val failStackBefore: Int,
    @Column(name = "final_success_rate", nullable = false) val finalSuccessRate: Double,
    @Column(name = "roll_value", nullable = false) val rollValue: Double,
    @Enumerated(EnumType.STRING) @Column(name = "result", nullable = false, length = 20)
    val result: EvolutionResult,
    @Column(name = "exp_after", nullable = false) val expAfter: Long,
    @Column(name = "fail_stack_after", nullable = false) val failStackAfter: Int,
    @Column(name = "policy_version", nullable = false) val policyVersion: Int,
) : BaseEntity()
