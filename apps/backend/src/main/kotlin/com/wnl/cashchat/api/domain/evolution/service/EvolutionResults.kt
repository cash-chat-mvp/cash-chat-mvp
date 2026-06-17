package com.wnl.cashchat.api.domain.evolution.service

data class EvolutionStateResult(
    val level: Int,
    val isMaxLevel: Boolean,
    val nextAttemptCost: Long?,
    val nextSuccessRate: Double?,
)

data class EvolutionAttemptResult(
    val success: Boolean,
    val fromLevel: Int,
    val resultLevel: Int,
    val cost: Long,
)