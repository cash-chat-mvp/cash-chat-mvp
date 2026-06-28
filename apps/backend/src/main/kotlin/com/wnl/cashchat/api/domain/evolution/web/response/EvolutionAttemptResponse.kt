package com.wnl.cashchat.api.domain.evolution.web.response

import com.wnl.cashchat.api.domain.evolution.service.EvolutionAttemptResult
import com.wnl.cashchat.api.domain.evolution.service.TimingGrade

data class EvolutionAttemptResponse(
    val success: Boolean,
    val fromLevel: Int,
    val resultLevel: Int,
    val cost: Long,
    val timingGrade: TimingGrade? = null,
    val timingBonusRate: Double? = null,
    val baseSuccessRate: Double? = null,
    val finalSuccessRate: Double? = null,
) {
    companion object {
        fun from(result: EvolutionAttemptResult) = EvolutionAttemptResponse(
            success = result.success,
            fromLevel = result.fromLevel,
            resultLevel = result.resultLevel,
            cost = result.cost,
            timingGrade = result.timingGrade,
            timingBonusRate = result.timingBonusRate,
            baseSuccessRate = result.baseSuccessRate,
            finalSuccessRate = result.finalSuccessRate,
        )
    }
}