package com.wnl.cashchat.api.domain.evolution.web.response

import com.wnl.cashchat.api.domain.evolution.persistence.entity.EvolutionAttempt
import com.wnl.cashchat.api.domain.evolution.persistence.entity.EvolutionResult
import java.time.Instant

data class EvolutionAttemptResponse(
    val id: Long,
    val result: EvolutionResult,
    val levelBefore: Int,
    val levelAfter: Int,
    val requiredExp: Long,
    val baseSuccessRate: Double,
    val failStackBefore: Int,
    val finalSuccessRate: Double,
    val expAfter: Long,
    val failStackAfter: Int,
    val policyVersion: Int,
    val createdAt: Instant,
) {
    companion object {
        fun from(a: EvolutionAttempt) = EvolutionAttemptResponse(
            id = a.id,
            result = a.result,
            levelBefore = a.levelBefore,
            levelAfter = a.levelAfter,
            requiredExp = a.requiredExp,
            baseSuccessRate = a.baseSuccessRate,
            failStackBefore = a.failStackBefore,
            finalSuccessRate = a.finalSuccessRate,
            expAfter = a.expAfter,
            failStackAfter = a.failStackAfter,
            policyVersion = a.policyVersion,
            createdAt = a.createdAt,
        )
    }
}
