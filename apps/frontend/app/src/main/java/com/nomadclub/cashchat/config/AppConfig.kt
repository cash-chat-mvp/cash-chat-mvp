package com.nomadclub.cashchat.config

import com.nomadclub.cashchat.BuildConfig

/**
 * 앱 전역 설정 진입점(세션 스냅샷).
 *
 * 계층형 폴백으로 값을 해석한다:
 * `Remote Config(활성값) → BuildConfig(빌드타임) → Google 테스트 ID`
 *
 * 광고/정책 값은 세션 시작 시 1회만 읽어 세션 도중 변하지 않는다(다음 실행에 반영).
 * 긴급 키(점검/강제업데이트)만 [RemoteConfigManager.gateState]로 reactive 하게 처리.
 *
 * > AdMob **앱 ID**는 Manifest/SDK 초기화에 필요해 빌드타임 전용 — RC로 교체 불가.
 */
data class AppConfig(
    // API
    val baseUrl: String,
    // AdMob
    val adsEnabled: Boolean,
    val admobAppId: String,
    val admobBannerAdUnitId: String,
    val admobInterstitialAdUnitId: String,
    val admobNativeAdUnitId: String,
    val admobRewardedAdUnitId: String,
    // Sentry
    val sentryDsn: String,
    // TNK Offerwall
    val tnkAppId: String,
    // Feature flags / 정책 (Remote Config)
    val offerwallEnabled: Boolean,
    val adChatInterval: Long,
    val rewardChatInterval: Long,
    val rewardRequired: Boolean,
    val interstitialTriggerAction: String,
) {
    companion object {
        /** Remote Config 미사용 환경(테스트 등)의 폴백 생성자: BuildConfig→테스트ID. */
        fun fromBuildConfig(): AppConfig = AppConfig(
            baseUrl = BuildConfig.BASE_URL,
            adsEnabled = true,
            admobAppId = BuildConfig.ADMOB_APP_ID.ifBlank { TestAdUnitIds.APP_ID },
            admobBannerAdUnitId = BuildConfig.ADMOB_BANNER_AD_UNIT_ID.ifBlank { TestAdUnitIds.BANNER },
            admobInterstitialAdUnitId = BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID.ifBlank { TestAdUnitIds.INTERSTITIAL },
            admobNativeAdUnitId = BuildConfig.ADMOB_NATIVE_AD_UNIT_ID.ifBlank { TestAdUnitIds.NATIVE },
            admobRewardedAdUnitId = BuildConfig.ADMOB_REWARDED_AD_UNIT_ID.ifBlank { TestAdUnitIds.REWARDED },
            sentryDsn = BuildConfig.SENTRY_DSN,
            tnkAppId = BuildConfig.TNK_APP_ID,
            offerwallEnabled = true,
            adChatInterval = 1L,
            rewardChatInterval = 3L,
            rewardRequired = true,
            interstitialTriggerAction = "new_chat",
        )

        /** RC(활성값)→BuildConfig→테스트ID 계층 폴백으로 해석. */
        fun resolve(rc: RemoteConfigManager): AppConfig = AppConfig(
            baseUrl = BuildConfig.BASE_URL,
            adsEnabled = rc.getBoolean(RemoteConfigKeys.ADS_ENABLED),
            // 앱 ID는 빌드타임 전용
            admobAppId = BuildConfig.ADMOB_APP_ID.ifBlank { TestAdUnitIds.APP_ID },
            admobBannerAdUnitId = adUnit(rc, RemoteConfigKeys.ADMOB_BANNER_AD_UNIT_ID, BuildConfig.ADMOB_BANNER_AD_UNIT_ID, TestAdUnitIds.BANNER),
            admobInterstitialAdUnitId = adUnit(rc, RemoteConfigKeys.ADMOB_INTERSTITIAL_AD_UNIT_ID, BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID, TestAdUnitIds.INTERSTITIAL),
            admobNativeAdUnitId = adUnit(rc, RemoteConfigKeys.ADMOB_NATIVE_AD_UNIT_ID, BuildConfig.ADMOB_NATIVE_AD_UNIT_ID, TestAdUnitIds.NATIVE),
            admobRewardedAdUnitId = adUnit(rc, RemoteConfigKeys.ADMOB_REWARDED_AD_UNIT_ID, BuildConfig.ADMOB_REWARDED_AD_UNIT_ID, TestAdUnitIds.REWARDED),
            sentryDsn = BuildConfig.SENTRY_DSN,
            tnkAppId = BuildConfig.TNK_APP_ID,
            offerwallEnabled = rc.getBoolean(RemoteConfigKeys.OFFERWALL_ENABLED),
            adChatInterval = rc.getLong(RemoteConfigKeys.AD_CHAT_INTERVAL),
            rewardChatInterval = rc.getLong(RemoteConfigKeys.REWARD_CHAT_INTERVAL),
            rewardRequired = rc.getBoolean(RemoteConfigKeys.REWARD_REQUIRED),
            interstitialTriggerAction = rc.getString(RemoteConfigKeys.INTERSTITIAL_TRIGGER_ACTION),
        )

        private fun adUnit(rc: RemoteConfigManager, key: String, buildTime: String, test: String): String =
            rc.getString(key).ifBlank { buildTime.ifBlank { test } }
    }
}
