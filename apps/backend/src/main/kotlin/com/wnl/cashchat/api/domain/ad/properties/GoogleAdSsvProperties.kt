package com.wnl.cashchat.api.domain.ad.properties

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.Duration

@Validated
@ConfigurationProperties(prefix = "app.ads.google")
data class GoogleAdSsvProperties(
    @field:NotBlank
    val ssvPublicKeysUri: String = "https://www.gstatic.com/admob/reward/verifier-keys.json",

    @field:PositiveDuration
    @field:MaxDuration(hours = 24)
    val publicKeyCacheTtl: Duration = Duration.ofHours(24),

    val rewardedAdUnitId: String = "",
) {
    fun isRewardedAdUnitValidationEnabled(): Boolean = rewardedAdUnitId.isNotBlank()
}
