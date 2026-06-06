package com.wnl.cashchat.api.domain.energy.properties

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "app.energy")
data class EnergyProperties(
    @field:Positive val maxEnergy: Int = 50,
    @field:PositiveOrZero val signupBonus: Int = 50,
    @field:DecimalMin("0.0") @field:DecimalMax("1.0") val postEvolutionRatio: Double = 0.5,
)
