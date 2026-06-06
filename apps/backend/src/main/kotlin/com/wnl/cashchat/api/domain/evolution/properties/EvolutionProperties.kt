package com.wnl.cashchat.api.domain.evolution.properties

import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * 진화 전이 규칙(app.evolution). rules 는 fromLevel 별 시도 비용·성공 확률.
 * 규칙이 없는 레벨(예: 최종 레벨)은 진화 불가(최대 레벨)로 본다.
 */
@Validated
@ConfigurationProperties(prefix = "app.evolution")
data class EvolutionProperties(
    val rules: List<LevelRule> = emptyList(),
) {
    data class LevelRule(
        val fromLevel: Int,
        @field:Positive val attemptCost: Long,
        val successRate: Double,
    )

    fun ruleFor(level: Int): LevelRule? = rules.firstOrNull { it.fromLevel == level }
}