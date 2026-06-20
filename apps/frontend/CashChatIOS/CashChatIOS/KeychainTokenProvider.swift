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

    // 동시에 여러 API가 401을 받아 refresh()를 호출해도 실제 갱신 요청은 1회만 수행하도록
    // 진행 중인 Task를 공유(coalesce)한다. RTR(Refresh Token Rotation) 환경에서 동시 갱신으로
    // 인한 토큰 무효화(Race Condition)를 방지한다.
    private let lock = NSLock()
    private var activeRefreshTask: Task<KotlinBoolean, Never>?

    init(baseUrl: String = AppConfig.apiBaseUrl) {
        self.baseUrl = baseUrl.hasSuffix("/") ? String(baseUrl.dropLast()) : baseUrl
    }

    // Kotlin: suspend fun accessToken(): String?
    func accessToken() async throws -> String? {
        KeychainHelper.get(forKey: "access_token")
    }

    // Kotlin: suspend fun refresh(): Boolean (non-null) -> Swift async 비옵셔널 KotlinBoolean
    func refresh() async throws -> KotlinBoolean {
        lock.lock()
        if let existing = activeRefreshTask {
            lock.unlock()
            return await existing.value
        }
        let task = Task<KotlinBoolean, Never> { [weak self] in
            guard let self else { return KotlinBoolean(bool: false) }
            let result = await self.performRefresh()
            // 완료된 Task 정리 — 다음 호출은 새 갱신을 수행한다.
            self.lock.lock()
            self.activeRefreshTask = nil
            self.lock.unlock()
            return result
        }
        activeRefreshTask = task
        lock.unlock()
        return await task.value
    }

    private func performRefresh() async -> KotlinBoolean {
        // 회원(Member)은 refresh token 회전, 게스트(GUEST)는 deviceToken으로 재인증.
        if let refreshToken = KeychainHelper.get(forKey: "refresh_token") {
            return await refreshWithToken(refreshToken)
        }
        return await reauthenticateGuest()
    }

    /// 회원 세션: POST /api/auth/refresh (Refresh Token Rotation)
    private func refreshWithToken(_ refreshToken: String) async -> KotlinBoolean {
        guard let url = URL(string: "\(baseUrl)/api/auth/refresh") else {
            return KotlinBoolean(bool: false)
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try? JSONSerialization.data(withJSONObject: ["refreshToken": refreshToken])

        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode),
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
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

    /// 게스트 세션: refresh token이 없으므로 deviceToken으로 새 accessToken을 재발급.
    /// POST /api/auth/guest?deviceToken={deviceToken} (Android 경로와 동일)
    private func reauthenticateGuest() async -> KotlinBoolean {
        guard let deviceToken = UserDefaults.standard.string(forKey: "device_token"),
              !deviceToken.isEmpty,
              var components = URLComponents(string: "\(baseUrl)/api/auth/guest") else {
            return KotlinBoolean(bool: false)
        }
        components.queryItems = [URLQueryItem(name: "deviceToken", value: deviceToken)]
        guard let url = components.url else {
            return KotlinBoolean(bool: false)
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"

        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode),
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let newAccess = json["accessToken"] as? String else {
                return KotlinBoolean(bool: false)
            }
            KeychainHelper.set(newAccess, forKey: "access_token")
            if let role = json["role"] as? String {
                KeychainHelper.set(role, forKey: "role")
            }
            return KotlinBoolean(bool: true)
        } catch {
            return KotlinBoolean(bool: false)
        }
    }
}
