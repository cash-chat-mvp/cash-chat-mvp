import Foundation

/// Firebase Remote Config 키 + 인앱 기본값.
/// Android(`RemoteConfigKeys.kt`)와 키 이름을 1:1로 맞춘다.
enum RemoteConfigKeys {
    // A. 광고
    static let adsEnabled = "ads_enabled"
    static let admobBannerAdUnitId = "admob_banner_ad_unit_id"
    static let admobInterstitialAdUnitId = "admob_interstitial_ad_unit_id"
    static let admobNativeAdUnitId = "admob_native_ad_unit_id"
    static let admobRewardedAdUnitId = "admob_rewarded_ad_unit_id"

    // B. 기능 플래그 / 긴급
    static let offerwallEnabled = "offerwall_enabled"
    static let maintenanceMode = "maintenance_mode"
    static let maintenanceMessage = "maintenance_message"
    static let forceUpdateMinVersion = "force_update_min_version"
    static let forceUpdateMessage = "force_update_message"

    // C. 정책 / 파라미터
    static let adChatInterval = "ad_chat_interval"
    static let rewardChatInterval = "reward_chat_interval"
    static let rewardRequired = "reward_required"
    static let interstitialTriggerAction = "interstitial_trigger_action"

    // D. 온디바이스 Gemma 모델
    /// 모델 파일(.litertlm) 다운로드 URL. 비워두면 shared 빌트인 기본값(HF) 사용.
    /// 자체 듀얼스택·Asia 리전 CDN URL로 교체 시 앱 업데이트 없이 반영된다.
    static let gemmaModelUrl = "gemma_model_url"

    /// 인앱 기본값. 광고 단위 ID는 의도적으로 비워둔다 → AppConfig가 Secrets→테스트ID로 폴백.
    static let defaults: [String: NSObject] = [
        adsEnabled: true as NSNumber,
        admobBannerAdUnitId: "" as NSString,
        admobInterstitialAdUnitId: "" as NSString,
        admobNativeAdUnitId: "" as NSString,
        admobRewardedAdUnitId: "" as NSString,
        offerwallEnabled: true as NSNumber,
        maintenanceMode: false as NSNumber,
        maintenanceMessage: "" as NSString,
        forceUpdateMinVersion: "" as NSString,
        forceUpdateMessage: "" as NSString,
        adChatInterval: 1 as NSNumber,
        rewardChatInterval: 3 as NSNumber,
        rewardRequired: true as NSNumber,
        interstitialTriggerAction: "new_chat" as NSString,
        gemmaModelUrl: "" as NSString,
    ]
}

/// Google 공식 테스트 광고 단위 ID (iOS). 계층형 폴백의 최종 단계.
enum TestAdUnitIds {
    static let appId = "ca-app-pub-3940256099942544~1458002511"
    static let banner = "ca-app-pub-3940256099942544/2934735716"
    static let interstitial = "ca-app-pub-3940256099942544/4411468910"
    static let native = "ca-app-pub-3940256099942544/3986624511"
    static let rewarded = "ca-app-pub-3940256099942544/1712485313"
}
