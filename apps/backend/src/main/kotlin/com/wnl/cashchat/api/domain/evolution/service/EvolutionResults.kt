package com.wnl.cashchat.api.domain.evolution.service

import java.time.Instant

data class EvolutionStateResult(
    val level: Int,
    val isMaxLevel: Boolean,
    val nextAttemptCost: Long?,
    val nextSuccessRate: Double?,
    val currentExp: Long,
)

data class EvolutionAttemptResult(
    val success: Boolean,
    val fromLevel: Int,
    val resultLevel: Int,
    val cost: Long,
)

data class EvolutionAttemptRecordResult(
    val success: Boolean,
    val fromLevel: Int,
    val resultLevel: Int,
    val cost: Long,
    val attemptedAt: Instant,
)