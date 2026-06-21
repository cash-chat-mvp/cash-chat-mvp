package com.wnl.cashchat.api.domain.economy.properties

import jakarta.validation.constraints.PositiveOrZero
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "app.evolution")
data class EvolutionProperties(
    val maxLevel: Int = 5,
    @field:PositiveOrZero val policyVersion: Int = 1,
    @field:PositiveOrZero val failStackBonus: Double = 0.10,
    val levels: List<LevelPolicy> = DEFAULT_LEVELS,
) {
    data class LevelPolicy(
        val level: Int,
        val requiredExp: Long,
        val baseSuccessRate: Double,
        val failKeepRatio: Double,
    )

    fun policyFor(level: Int): LevelPolicy? = levels.firstOrNull { it.level == level }

    companion object {
        val DEFAULT_LEVELS = listOf(
            LevelPolicy(1, 30, 0.80, 0.0),
            LevelPolicy(2, 100, 0.60, 0.0),
            LevelPolicy(3, 300, 0.35, 0.20),
            LevelPolicy(4, 1000, 0.15, 0.30),
        )
    }
}
