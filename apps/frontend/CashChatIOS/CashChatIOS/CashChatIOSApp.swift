import SwiftUI
import GoogleSignIn
import GoogleMobileAds
import CashChatShared

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
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(themeSettings)
                .preferredColorScheme(themeSettings.themeMode.colorScheme)
                .onOpenURL { url in
                    GIDSignIn.sharedInstance.handle(url)
                }
        }
    }
}
