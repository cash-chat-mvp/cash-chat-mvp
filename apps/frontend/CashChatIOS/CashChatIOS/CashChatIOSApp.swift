import SwiftUI
import GoogleSignIn
import CashChatShared

@main
struct CashChatIOSApp: App {
    @StateObject private var themeSettings = ThemeSettings()

    init() {
        KoinIosKt.doInitKoin(baseUrl: AppConfig.apiBaseUrl, tokenProvider: KeychainTokenProvider())
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
