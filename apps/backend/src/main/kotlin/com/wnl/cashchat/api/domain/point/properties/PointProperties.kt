package com.wnl.cashchat.api.domain.point.properties

import jakarta.validation.constraints.PositiveOrZero
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "app.points")
data class PointProperties(
    @field:PositiveOrZero
    val initialBalance: Long = 1,
)
