import Foundation
import CashChatShared

/// shared TokenProvider 를 iOS 저장소로 위임.
/// access/refresh/role 은 Keychain, deviceToken 은 UserDefaults(기존 AppState 규약과 일치).
final class KeychainTokenProvider: TokenProvider {
    private enum Keys {
        static let accessToken = "access_token"
        static let refreshToken = "refresh_token"
        static let role = "role"
        static let deviceToken = "device_token"
    }

    func accessToken() -> String? { KeychainHelper.get(forKey: Keys.accessToken) }
    func refreshToken() -> String? { KeychainHelper.get(forKey: Keys.refreshToken) }
    func role() -> String? { KeychainHelper.get(forKey: Keys.role) }
    func deviceToken() -> String? { UserDefaults.standard.string(forKey: Keys.deviceToken) }
    func updateTokens(accessToken: String, refreshToken: String) {
        KeychainHelper.set(accessToken, forKey: Keys.accessToken)
        KeychainHelper.set(refreshToken, forKey: Keys.refreshToken)
    }
}
