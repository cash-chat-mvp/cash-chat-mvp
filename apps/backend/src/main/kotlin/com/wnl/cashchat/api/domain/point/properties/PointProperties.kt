package com.wnl.cashchat.api.domain.point.properties

import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "app.points")
data class PointProperties(
    @field:Positive
    val initialBalance: Long = 1,
)
