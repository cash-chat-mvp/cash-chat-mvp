import SwiftUI
import UIKit
import GoogleSignIn
import GoogleMobileAds
import FirebaseCore
import CashChatShared
import TnkRwdSdk2

@main
struct CashChatIOSApp: App {
    @StateObject private var themeSettings = ThemeSettings()
    @StateObject private var remoteConfig = RemoteConfigManager.shared

    init() {
        // Firebase 초기화. RemoteConfig/Analytics 사용 전에 가장 먼저 호출해야 한다.
        // (GoogleService-Info.plist 가 번들에 있어야 하며, 없으면 런타임 크래시한다.)
        FirebaseApp.configure()
        // Remote Config 초기화(캐시 즉시 반영 + 최신 값 fetch).
        RemoteConfigManager.shared.initialize()
        // 앱 생명주기당 1회 호출. shared Koin 그래프 초기화(데이터 레이어 접근 다리).
        KoinIosKt.doInitKoin(
            baseUrl: AppConfig.apiBaseUrl,
            tokenProvider: KeychainTokenProvider(),
            adChatInterval: Int64(AppConfig.adChatInterval),
            gemmaEngine: SwiftBackedLocalLlmEngine(bridge: GemmaLlmBridge())
        )
        // AdMob 초기화 (리워드 광고). 앱 생명주기당 1회.
        MobileAds.shared.start(completionHandler: nil)
        // TNK 오퍼월 SDK 초기화. 앱 생명주기당 1회.
        // initInstance 만으로는 세션이 완전히 시작되지 않아 오퍼가 안 내려온다 —
        // 가이드대로 applicationStarted() 까지 호출해야 한다(Android 의 applicationStarted 와 동일).
        TnkSession.initInstance(appId: AppConfig.tnkAppId)
        TnkSession.sharedInstance()?.applicationStarted()
    }

    var body: some Scene {
        WindowGroup {
            // 긴급 게이트(점검 모드 / 강제 업데이트)는 모든 화면보다 우선한다.
            if remoteConfig.gateState == .none {
                ContentView()
                    .environmentObject(themeSettings)
                    .preferredColorScheme(themeSettings.themeMode.colorScheme)
                    .onOpenURL { url in
                        GIDSignIn.sharedInstance.handle(url)
                    }
                    .onAppear {
                        // 첫 실행 시 ATT(IDFA 추적) 허용 요청 — TNK 오퍼월/Analytics 식별에 필요.
                        TrackingAuthorization.requestAtLaunchIfNeeded()
                    }
            } else {
                AppGateView(state: remoteConfig.gateState, onUpdate: Self.openAppStore)
                    .preferredColorScheme(themeSettings.themeMode.colorScheme)
            }
        }
    }

    /// 강제 업데이트 시 App Store로 이동.
    /// 출시 후 실제 App Store 앱 ID로 `appStoreId`를 채워야 정확한 페이지로 연결된다.
    private static let appStoreId = "6768071606" // App Store Connect 앱 정보의 Apple ID(자동 생성 숫자)
    private static func openAppStore() {
        let urlString = appStoreId.isEmpty
            ? "itms-apps://apps.apple.com/"
            : "itms-apps://apps.apple.com/app/id\(appStoreId)"
        if let url = URL(string: urlString) {
            UIApplication.shared.open(url)
        }
    }
}
