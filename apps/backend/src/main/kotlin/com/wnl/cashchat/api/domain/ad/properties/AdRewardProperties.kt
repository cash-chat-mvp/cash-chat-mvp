package com.wnl.cashchat.api.domain.ad.properties

import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.Duration

@Validated
@ConfigurationProperties(prefix = "app.ads.reward")
data class AdRewardProperties(
    @field:Positive
    val coinAmount: Long = 40,

    @field:Positive
    val dailyLimit: Int = 10,

    @field:PositiveDuration
    val nonceTtl: Duration = Duration.ofMinutes(10),
)
