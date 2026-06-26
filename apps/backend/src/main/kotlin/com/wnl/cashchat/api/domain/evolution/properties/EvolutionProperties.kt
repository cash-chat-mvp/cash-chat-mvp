package com.wnl.cashchat.api.domain.evolution.properties

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Positive
import java.time.Duration
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
    val timing: TimingConfig = TimingConfig(),
) {
    data class LevelRule(
        val fromLevel: Int,
        @field:Positive val attemptCost: Long,
        @field:DecimalMin("0.0") @field:DecimalMax("1.0") val successRate: Double,
    )

    /** 길게누르기 타이밍 보너스 설정. 기본값은 FE 하드코딩 상수와 일치해야 한다. */
    data class TimingConfig(
        val minimumHoldMs: Long = 600,
        val cycleDurationMs: Long = 1800,
        val perfectStart: Double = 0.45,
        val perfectEnd: Double = 0.55,
        val greatStart: Double = 0.38,
        val greatEnd: Double = 0.62,
        val sessionTtl: Duration = Duration.ofMinutes(2),
        val clockSkewToleranceMs: Long = 2000,
    )

    fun ruleFor(level: Int): LevelRule? = rules.firstOrNull { it.fromLevel == level }
}