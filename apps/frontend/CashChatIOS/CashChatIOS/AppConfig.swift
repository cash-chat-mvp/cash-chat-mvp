import Foundation

/// 앱 전역 설정 진입점(세션 스냅샷).
///
/// 광고/정책 값은 계층형 폴백으로 해석한다:
/// `Remote Config(활성값) → Secrets(빌드타임) → Google 테스트 ID`
/// 정적 프로퍼티라 최초 접근 시 1회만 계산되어 세션 도중 변하지 않는다(다음 실행 반영).
///
/// > AdMob **앱 ID**는 Info.plist/SDK 초기화에 필요해 빌드타임 전용 — RC로 교체 불가.
enum AppConfig {
    static let googleIOSClientId: String = required(Secrets.googleIOSClientId, key: "googleIOSClientId")
    static let googleWebClientId: String = required(Secrets.googleWebClientId, key: "googleWebClientId")
    static let apiBaseUrl: String = required(Secrets.apiBaseUrl, key: "apiBaseUrl")
    static let tnkAppId: String = required(Secrets.tnkAppId, key: "tnkAppId")

    // AdMob — RC→Secrets→테스트ID 폴백
    static let adsEnabled: Bool = RemoteConfigManager.shared.bool(RemoteConfigKeys.adsEnabled)
    static let admobAppId: String = fallback(Secrets.admobAppId, test: TestAdUnitIds.appId)
    static let admobRewardedAdUnitId: String = adUnit(RemoteConfigKeys.admobRewardedAdUnitId, Secrets.admobRewardedAdUnitId, TestAdUnitIds.rewarded)
    static let admobBannerAdUnitId: String = adUnit(RemoteConfigKeys.admobBannerAdUnitId, Secrets.admobBannerAdUnitId, TestAdUnitIds.banner)
    static let admobInterstitialAdUnitId: String = adUnit(RemoteConfigKeys.admobInterstitialAdUnitId, Secrets.admobInterstitialAdUnitId, TestAdUnitIds.interstitial)
    static let admobNativeAdUnitId: String = adUnit(RemoteConfigKeys.admobNativeAdUnitId, Secrets.admobNativeAdUnitId, TestAdUnitIds.native)

    // Feature flags / 정책 (Remote Config)
    static let offerwallEnabled: Bool = RemoteConfigManager.shared.bool(RemoteConfigKeys.offerwallEnabled)
    // RC 오입력(0/음수) 방어 — 간격은 최소 1 이상이어야 한다(modulo/카운트 계산 안전).
    static let adChatInterval: Int = max(1, RemoteConfigManager.shared.number(RemoteConfigKeys.adChatInterval).intValue)
    static let rewardChatInterval: Int = max(1, RemoteConfigManager.shared.number(RemoteConfigKeys.rewardChatInterval).intValue)
    static let rewardRequired: Bool = RemoteConfigManager.shared.bool(RemoteConfigKeys.rewardRequired)
    static let interstitialTriggerAction: String = RemoteConfigManager.shared.string(RemoteConfigKeys.interstitialTriggerAction)

    /// RC→Secrets→테스트ID 폴백.
    private static func adUnit(_ rcKey: String, _ secret: String, _ test: String) -> String {
        let rc = RemoteConfigManager.shared.string(rcKey).trimmingCharacters(in: .whitespacesAndNewlines)
        if !rc.isEmpty { return rc }
        return fallback(secret, test: test)
    }

    /// Secrets 값이 비었거나 placeholder면 테스트 ID로 폴백(앱 크래시 방지).
    private static func fallback(_ secret: String, test: String) -> String {
        let trimmed = secret.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmed.isEmpty, !trimmed.hasPrefix("YOUR_") { return trimmed }
        return test
    }

    /// 필수 비밀값. 비었거나 placeholder면 즉시 실패(개발 중 설정 누락 조기 발견).
    private static func required(_ value: String, key: String) -> String {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, !trimmed.hasPrefix("YOUR_") else {
            preconditionFailure(
                "[\(key)] 값이 설정되지 않았습니다.\n" +
                "Secrets.swift를 생성하고 실제 값을 입력하세요."
            )
        }
        return trimmed
    }
}
