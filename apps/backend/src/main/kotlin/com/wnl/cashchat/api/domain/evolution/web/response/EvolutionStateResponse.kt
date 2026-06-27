package com.wnl.cashchat.api.domain.evolution.web.response

import com.fasterxml.jackson.annotation.JsonProperty
import com.wnl.cashchat.api.domain.evolution.service.EvolutionStateResult

data class EvolutionStateResponse(
    val level: Int,
    // Jackson 은 Kotlin boolean 의 is 접두사를 떼어 "maxLevel" 로 직렬화한다.
    // 프론트(kotlinx.serialization)는 "isMaxLevel"(필수)을 기대하므로 직렬화(@get)·역직렬화(@param)
    // 양쪽 이름을 고정한다(CC-352). @get 없이 @param 만 두면 getter 기반 직렬화가 다시 maxLevel 로 샌다.
    @get:JsonProperty("isMaxLevel")
    @param:JsonProperty("isMaxLevel")
    val isMaxLevel: Boolean,
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