package com.nomadclub.cashchat.shared.ads

import com.nomadclub.cashchat.shared.core.config.FeatureFlags

/**
 * 배너 광고 노출 위치. 광고 단위 ID는 슬롯 공통(AppConfig.admobBannerAdUnitId)이며,
 * 슬롯은 Analytics 구분·위치별 제어 목적의 식별자다.
 */
enum class BannerAdSlot(val analyticsName: String) {
    CHAT_TOP("chat_top"),
    BENEFIT_TOP("benefit_top");

    /** 이 슬롯에 배너를 노출해도 되는지. 현재는 전역 플래그만 본다. */
    fun isEnabled(): Boolean = FeatureFlags.BANNER_ADS
}
