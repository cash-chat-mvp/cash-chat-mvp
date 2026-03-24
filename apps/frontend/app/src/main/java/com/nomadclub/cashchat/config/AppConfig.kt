package com.nomadclub.cashchat.config

import com.nomadclub.cashchat.BuildConfig

/**
 * 앱 전역 설정 진입점.
 * 모든 키/ID는 BuildConfig(flavor별)를 통해 주입되며 소스코드에 직접 하드코딩되지 않습니다.
 *
 * local.properties (dev 로컬) 또는 CI 환경변수 (prod) → BuildConfig → AppConfig
 */
data class AppConfig(
    // AdMob
    val admobAppId: String,
    val admobBannerAdUnitId: String,
    val admobInterstitialAdUnitId: String,
    val admobNativeAdUnitId: String,
    val admobRewardedAdUnitId: String,
    // Sentry
    val sentryDsn: String,
) {
    companion object {
        fun fromBuildConfig(): AppConfig = AppConfig(
            admobAppId = BuildConfig.ADMOB_APP_ID,
            admobBannerAdUnitId = BuildConfig.ADMOB_BANNER_AD_UNIT_ID,
            admobInterstitialAdUnitId = BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID,
            admobNativeAdUnitId = BuildConfig.ADMOB_NATIVE_AD_UNIT_ID,
            admobRewardedAdUnitId = BuildConfig.ADMOB_REWARDED_AD_UNIT_ID,
            sentryDsn = BuildConfig.SENTRY_DSN,
        )
    }
}
