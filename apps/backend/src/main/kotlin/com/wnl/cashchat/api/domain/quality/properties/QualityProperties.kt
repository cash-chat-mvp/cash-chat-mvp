package com.wnl.cashchat.api.domain.quality.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * 공용 품질 풀 정책 (app.quality).
 * poolSafetyFloorCentiPt: throttleScale 기준 하한 (default 500,000 centi-pt = 5,000 pt).
 * premiumDailyCapPerUser: 유저 1인당 일일 프리미엄 사용 상한 (default 50).
 */
@Validated
@ConfigurationProperties(prefix = "app.quality")
data class QualityProperties(
    val poolSafetyFloorCentiPt: Long = 500_000L,
    val premiumDailyCapPerUser: Int = 50,
)
