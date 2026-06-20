package com.wnl.cashchat.api.domain.evolution.web.response

import com.wnl.cashchat.api.domain.evolution.service.EvolutionStateResult

data class EvolutionStateResponse(
    val level: Int,
    val isMaxLevel: Boolean,
    val nextAttemptCost: Long?,
    val nextSuccessRate: Double?,
) {
    companion object {
        fun from(result: EvolutionStateResult) = EvolutionStateResponse(
            level = result.level,
            isMaxLevel = result.isMaxLevel,
            nextAttemptCost = result.nextAttemptCost,
            nextSuccessRate = result.nextSuccessRate,
        )
    }
}