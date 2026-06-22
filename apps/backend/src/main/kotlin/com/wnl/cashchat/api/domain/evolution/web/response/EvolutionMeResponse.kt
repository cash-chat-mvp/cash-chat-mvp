package com.wnl.cashchat.api.domain.evolution.web.response

data class EvolutionMeResponse(
    val level: Int,
    val exp: Long,
    val failStack: Int,
    val maxLevel: Int,
    val requiredExp: Long?,
    val baseSuccessRate: Double?,
    val finalSuccessRate: Double?,
    val canAttempt: Boolean,
)
