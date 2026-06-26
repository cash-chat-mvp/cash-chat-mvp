package com.wnl.cashchat.api.domain.evolution.web.response

import com.wnl.cashchat.api.domain.evolution.service.EvolutionAttemptRecordResult
import java.time.Instant

data class EvolutionAttemptsResponse(
    val attempts: List<EvolutionAttemptRecordResponse>,
) {
    companion object {
        fun from(records: List<EvolutionAttemptRecordResult>) =
            EvolutionAttemptsResponse(records.map { EvolutionAttemptRecordResponse.from(it) })
    }
}

data class EvolutionAttemptRecordResponse(
    val success: Boolean,
    val fromLevel: Int,
    val resultLevel: Int,
    val cost: Long,
    val attemptedAt: Instant,
) {
    companion object {
        fun from(r: EvolutionAttemptRecordResult) = EvolutionAttemptRecordResponse(
            success = r.success,
            fromLevel = r.fromLevel,
            resultLevel = r.resultLevel,
            cost = r.cost,
            attemptedAt = r.attemptedAt,
        )
    }
}
