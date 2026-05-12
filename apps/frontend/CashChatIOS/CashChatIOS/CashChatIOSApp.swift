import SwiftUI
import GoogleSignIn

@main
struct CashChatIOSApp: App {
    @StateObject private var themeSettings = ThemeSettings()

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
