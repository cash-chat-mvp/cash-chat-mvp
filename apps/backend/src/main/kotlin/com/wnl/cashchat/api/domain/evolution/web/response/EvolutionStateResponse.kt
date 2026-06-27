package com.wnl.cashchat.api.domain.evolution.web.response

import com.fasterxml.jackson.annotation.JsonProperty
import com.wnl.cashchat.api.domain.evolution.service.EvolutionStateResult

data class EvolutionStateResponse(
    val level: Int,
    // Jackson 은 Kotlin boolean 의 is 접두사를 떼어 "maxLevel" 로 직렬화한다.
    // 프론트(kotlinx.serialization)는 "isMaxLevel"(필수)을 기대하므로 명시적으로 고정한다(CC-352).
    @get:JsonProperty("isMaxLevel") val isMaxLevel: Boolean,
    val nextAttemptCost: Long?,
    val nextSuccessRate: Double?,
    val currentExp: Long,
) {
    companion object {
        fun from(result: EvolutionStateResult) = EvolutionStateResponse(
            level = result.level,
            isMaxLevel = result.isMaxLevel,
            nextAttemptCost = result.nextAttemptCost,
            nextSuccessRate = result.nextSuccessRate,
            currentExp = result.currentExp,
        )
    }
}