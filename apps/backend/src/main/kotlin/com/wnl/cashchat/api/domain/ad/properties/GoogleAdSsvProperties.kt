package com.wnl.cashchat.api.domain.ad.properties

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.Duration
import java.time.Instant

@Validated
@ConfigurationProperties(prefix = "app.ads.google")
data class GoogleAdSsvProperties(
    @field:NotBlank
    val ssvPublicKeysUri: String = "https://www.gstatic.com/admob/reward/verifier-keys.json",

    @field:PositiveDuration
    @field:MaxDuration(hours = 24)
    val publicKeyCacheTtl: Duration = Duration.ofHours(24),

    // Android·iOS 는 각각 별도의 보상형 광고 단위를 사용하므로 복수 ID 를 허용한다(콤마 구분 바인딩).
    // 비어 있으면 ad_unit 검증을 건너뛴다.
    val rewardedAdUnitIds: List<String> = emptyList(),

    // 콜백 timestamp 가 현재 시각 기준 이 만큼 과거보다 더 오래면 재생(replay)으로 보고 미적립한다.
    @field:PositiveDuration
    val timestampTolerance: Duration = Duration.ofHours(1),

    // 서버-구글 시계 오차 허용. 콜백 timestamp 가 이 만큼보다 더 미래면 미적립한다.
    @field:PositiveDuration
    val timestampFutureSkew: Duration = Duration.ofMinutes(5),
) {
    private val allowedAdUnitIds: Set<String> = rewardedAdUnitIds.filter { it.isNotBlank() }.toSet()

    fun isRewardedAdUnitValidationEnabled(): Boolean = allowedAdUnitIds.isNotEmpty()

    fun isAllowedAdUnit(adUnit: String): Boolean = adUnit in allowedAdUnitIds

    fun isTimestampFresh(timestampMillis: Long, now: Instant): Boolean {
        val eventTime = Instant.ofEpochMilli(timestampMillis)
        return !eventTime.isBefore(now.minus(timestampTolerance)) &&
            !eventTime.isAfter(now.plus(timestampFutureSkew))
    }
}
