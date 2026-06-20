package com.wnl.cashchat.api.domain.evolution.web.response

import com.wnl.cashchat.api.domain.evolution.service.EvolutionAttemptResult

data class EvolutionAttemptResponse(
    val success: Boolean,
    val fromLevel: Int,
    val resultLevel: Int,
    val cost: Long,
) {
    companion object {
        fun from(result: EvolutionAttemptResult) = EvolutionAttemptResponse(
            success = result.success,
            fromLevel = result.fromLevel,
            resultLevel = result.resultLevel,
            cost = result.cost,
        )
    }
}