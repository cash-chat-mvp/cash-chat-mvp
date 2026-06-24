package com.nomadclub.cashchat.config

/**
 * Firebase Remote Config 키 + 인앱 기본값 정의.
 *
 * iOS(`RemoteConfigKeys.swift`)와 키 이름을 1:1로 맞춘다.
 * 광고 단위 ID처럼 플랫폼이 다른 값은 Firebase 콘솔의 "Platform" 조건으로
 * 같은 키가 플랫폼별 값으로 풀리게 한다(코드 키 상수는 양 플랫폼 동일).
 */
object RemoteConfigKeys {
    // A. 광고
    const val ADS_ENABLED = "ads_enabled"
    const val ADMOB_BANNER_AD_UNIT_ID = "admob_banner_ad_unit_id"
    const val ADMOB_INTERSTITIAL_AD_UNIT_ID = "admob_interstitial_ad_unit_id"
    const val ADMOB_NATIVE_AD_UNIT_ID = "admob_native_ad_unit_id"
    const val ADMOB_REWARDED_AD_UNIT_ID = "admob_rewarded_ad_unit_id"

    // B. 기능 플래그 / 긴급
    const val OFFERWALL_ENABLED = "offerwall_enabled"
    const val MAINTENANCE_MODE = "maintenance_mode"
    const val MAINTENANCE_MESSAGE = "maintenance_message"
    const val FORCE_UPDATE_MIN_VERSION = "force_update_min_version"
    const val FORCE_UPDATE_MESSAGE = "force_update_message"

    // C. 정책 / 파라미터
    const val AD_CHAT_INTERVAL = "ad_chat_interval"
    const val REWARD_CHAT_INTERVAL = "reward_chat_interval"
    const val REWARD_REQUIRED = "reward_required"
    const val INTERSTITIAL_TRIGGER_ACTION = "interstitial_trigger_action"

    /**
     * 인앱 기본값. 첫 실행/오프라인에서 fetch 전에도 안전한 동작을 보장한다.
     * 광고 단위 ID는 의도적으로 비워둔다 → [AppConfig]가 BuildConfig→테스트ID로 폴백.
     */
    val DEFAULTS: Map<String, Any> = mapOf(
        ADS_ENABLED to true,
        ADMOB_BANNER_AD_UNIT_ID to "",
        ADMOB_INTERSTITIAL_AD_UNIT_ID to "",
        ADMOB_NATIVE_AD_UNIT_ID to "",
        ADMOB_REWARDED_AD_UNIT_ID to "",
        OFFERWALL_ENABLED to true,
        MAINTENANCE_MODE to false,
        MAINTENANCE_MESSAGE to "",
        FORCE_UPDATE_MIN_VERSION to "",
        FORCE_UPDATE_MESSAGE to "",
        AD_CHAT_INTERVAL to 1L,
        REWARD_CHAT_INTERVAL to 3L,
        REWARD_REQUIRED to true,
        INTERSTITIAL_TRIGGER_ACTION to "new_chat",
    )
}

/** Google 공식 테스트 광고 단위 ID (Android). 계층형 폴백의 최종 단계. */
object TestAdUnitIds {
    const val APP_ID = "ca-app-pub-3940256099942544~3347511713"
    const val BANNER = "ca-app-pub-3940256099942544/6300978111"
    const val INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
    const val NATIVE = "ca-app-pub-3940256099942544/2247696110"
    const val REWARDED = "ca-app-pub-3940256099942544/5224354917"
}
