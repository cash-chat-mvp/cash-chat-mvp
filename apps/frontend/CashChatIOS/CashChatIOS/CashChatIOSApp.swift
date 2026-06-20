import SwiftUI
import GoogleSignIn
import GoogleMobileAds
import CashChatShared
import TnkRwdSdk2

@main
struct CashChatIOSApp: App {
    @StateObject private var themeSettings = ThemeSettings()

    init() {
        // 앱 생명주기당 1회 호출. shared Koin 그래프 초기화(데이터 레이어 접근 다리).
        KoinIosKt.doInitKoin(
            baseUrl: AppConfig.apiBaseUrl,
            tokenProvider: KeychainTokenProvider()
        )
        // AdMob 초기화 (리워드 광고). 앱 생명주기당 1회.
        MobileAds.shared.start(completionHandler: nil)
        // TNK 오퍼월 SDK 초기화. 앱 생명주기당 1회.
        TnkSession.initInstance(appId: AppConfig.tnkAppId)
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(themeSettings)
                .preferredColorScheme(themeSettings.themeMode.colorScheme)
                .onOpenURL { url in
                    GIDSignIn.sharedInstance.handle(url)
                }
                .onAppear {
                    // ATT(IDFA 추적) 허용 요청 — TNK 오퍼월이 단말기를 식별해 오퍼를 내려주려면 필요.
                    TrackingAuthorization.requestIfNeeded()
                }
        }
    }
}
