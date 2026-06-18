import Foundation
import CashChatShared

/// 브랜치 shared `TokenProvider` 의 iOS 구현.
/// 토큰 저장/조회는 기존 KeychainHelper, 재발급은 POST /api/auth/refresh 를 사용한다.
///
/// 주의: Kotlin suspend 함수의 Swift 브리지 시그니처는 생성된 `CashChatShared` 헤더를 따른다.
/// (`Boolean` -> `KotlinBoolean`, nullable `String?` -> `String?`). 헤더가 completionHandler
/// 형태로 노출되면 async 대신 completionHandler 클로저로 구현하도록 조정한다.
final class KeychainTokenProvider: NSObject, TokenProvider {
    private let baseUrl: String

    init(baseUrl: String = AppConfig.apiBaseUrl) {
        self.baseUrl = baseUrl.hasSuffix("/") ? String(baseUrl.dropLast()) : baseUrl
    }

    // Kotlin: suspend fun accessToken(): String?
    func accessToken() async throws -> String? {
        KeychainHelper.get(forKey: "access_token")
    }

    // Kotlin: suspend fun refresh(): Boolean (non-null) -> Swift async 비옵셔널 KotlinBoolean
    func refresh() async throws -> KotlinBoolean {
        guard let refreshToken = KeychainHelper.get(forKey: "refresh_token") else {
            return KotlinBoolean(bool: false)
        }
        guard let url = URL(string: "\(baseUrl)/api/auth/refresh") else {
            return KotlinBoolean(bool: false)
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try? JSONSerialization.data(withJSONObject: ["refreshToken": refreshToken])

        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
                return KotlinBoolean(bool: false)
            }
            guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let newAccess = json["accessToken"] as? String else {
                return KotlinBoolean(bool: false)
            }
            KeychainHelper.set(newAccess, forKey: "access_token")
            if let newRefresh = json["refreshToken"] as? String {
                KeychainHelper.set(newRefresh, forKey: "refresh_token")
            }
            return KotlinBoolean(bool: true)
        } catch {
            return KotlinBoolean(bool: false)
        }
    }
}
