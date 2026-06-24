import Foundation
import Combine
import FirebaseRemoteConfig

/// 앱 전역 차단 상태. Android `AppGateState`와 대응.
enum AppGateState: Equatable {
    case none
    case maintenance(message: String)
    case forceUpdate(message: String)
}

/// Firebase Remote Config 래퍼 (Android `RemoteConfigManager.kt`와 동형).
///
/// fetch/activate 전략(혼합):
///  - 시작 시 캐시된 값을 즉시 activate → 이번 세션은 직전 값으로 안정 동작.
///  - 동시에 최신 값을 fetchAndActivate → 완료되면 긴급 게이트(점검/강제업데이트)만 재평가.
///  - 광고/정책 값은 `AppConfig`가 1회만 읽어 세션 내 변동 없음.
///
/// `minimumFetchInterval`: dev=0(즉시 테스트), prod=12h.
final class RemoteConfigManager: ObservableObject {
    static let shared = RemoteConfigManager()

    /// 긴급 게이트 상태. 메인 스레드에서만 갱신된다.
    @Published private(set) var gateState: AppGateState = .none

    /// `RemoteConfig.remoteConfig()`는 `FirebaseApp.configure()` 이후에만 접근 가능하므로 lazy로 둔다.
    /// (App의 @StateObject 기본값이 init 본문보다 먼저 평가되는 순서 문제 회피.)
    private lazy var remoteConfig: RemoteConfig = RemoteConfig.remoteConfig()
    private let appVersion: String

    init(appVersion: String = (Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String) ?? "0") {
        self.appVersion = appVersion
    }

    /// 앱 시작 시 1회 호출(`FirebaseApp.configure()` 이후).
    func initialize() {
        let settings = RemoteConfigSettings()
        #if DEBUG
        settings.minimumFetchInterval = 0
        #else
        settings.minimumFetchInterval = 43_200 // 12h
        #endif
        remoteConfig.configSettings = settings
        remoteConfig.setDefaults(RemoteConfigKeys.defaults)

        // 1) 캐시된 값 즉시 반영 → 게이트 1차 평가
        remoteConfig.activate { [weak self] _, _ in
            self?.recomputeGateOnMain()
        }
        // 2) 최신 값 fetch → 긴급 게이트 reactive 재평가
        remoteConfig.fetchAndActivate { [weak self] _, error in
            if let error = error {
                print("[RemoteConfig] fetch 실패 — 캐시/기본값 사용: \(error)")
            }
            self?.recomputeGateOnMain()
        }
    }

    func string(_ key: String) -> String { remoteConfig.configValue(forKey: key).stringValue }
    func bool(_ key: String) -> Bool { remoteConfig.configValue(forKey: key).boolValue }
    func number(_ key: String) -> NSNumber { remoteConfig.configValue(forKey: key).numberValue }

    private func recomputeGateOnMain() {
        let next: AppGateState
        if bool(RemoteConfigKeys.maintenanceMode) {
            next = .maintenance(message: string(RemoteConfigKeys.maintenanceMessage))
        } else if isUpdateRequired() {
            next = .forceUpdate(message: string(RemoteConfigKeys.forceUpdateMessage))
        } else {
            next = .none
        }
        DispatchQueue.main.async { [weak self] in self?.gateState = next }
    }

    private func isUpdateRequired() -> Bool {
        let min = string(RemoteConfigKeys.forceUpdateMinVersion)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        guard !min.isEmpty else { return false }
        return Self.compareVersions(appVersion, min) < 0
    }

    /// semver 단순 비교. a<b → 음수. 각 파트 숫자만 비교(접미사 무시).
    static func compareVersions(_ a: String, _ b: String) -> Int {
        let pa = a.split(separator: ".").map { Int($0.prefix { $0.isNumber }) ?? 0 }
        let pb = b.split(separator: ".").map { Int($0.prefix { $0.isNumber }) ?? 0 }
        let n = max(pa.count, pb.count)
        for i in 0..<n {
            let x = i < pa.count ? pa[i] : 0
            let y = i < pb.count ? pb[i] : 0
            if x != y { return x - y }
        }
        return 0
    }
}
