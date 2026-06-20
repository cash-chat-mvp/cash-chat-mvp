import AppTrackingTransparency
import AdSupport
import UIKit

/// App Tracking Transparency(ATT) 권한 요청/안내 헬퍼.
///
/// iOS 14+ 에서는 ATT 허용을 받기 전까지 IDFA 가 전부 0(`00000000-...`)으로 반환된다.
/// TNK 오퍼월은 이 IDFA 로 단말기를 식별해 (테스트) 오퍼를 내려주므로,
/// ATT 허용을 받지 못하면 오퍼월 컨텐츠가 비어 보인다.
///
/// 주의: 사용자가 한 번 "거부"하면 시스템 프롬프트는 다시 표시할 수 없다
/// (`requestTrackingAuthorization` 은 `.notDetermined` 일 때만 프롬프트를 띄움).
/// 거부 이후 재허용은 설정 앱에서만 가능하므로, 이 경우 설정 이동 안내 알럿을 띄운다.
enum TrackingAuthorization {
    private static var requestedAtLaunch = false

    /// 앱 시작 시 ATT 권한을 1회 요청한다(첫 실행 프롬프트).
    /// 시스템 프롬프트는 앱이 active 상태일 때만 표시되므로 활성 직후 약간 지연해 호출한다.
    @MainActor
    static func requestAtLaunchIfNeeded() {
        guard !requestedAtLaunch else { return }
        requestedAtLaunch = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
            guard ATTrackingManager.trackingAuthorizationStatus == .notDetermined else {
                logIdfa(); return
            }
            ATTrackingManager.requestTrackingAuthorization { _ in logIdfa() }
        }
    }

    /// 추적이 허용된 경우에만 [onAuthorized] 를 실행한다.
    /// - 미결정(.notDetermined): 시스템 프롬프트를 띄우고, 허용 시 진행 / 거부 시 설정 안내 알럿.
    /// - 거부/제한(.denied/.restricted): 사유 설명 + "설정으로 이동" 알럿(시스템 재프롬프트 불가).
    /// - 허용(.authorized): 즉시 진행.
    @MainActor
    static func ensureAuthorized(from presenter: UIViewController, onAuthorized: @escaping () -> Void) {
        switch ATTrackingManager.trackingAuthorizationStatus {
        case .authorized:
            onAuthorized()
        case .notDetermined:
            ATTrackingManager.requestTrackingAuthorization { status in
                DispatchQueue.main.async {
                    logIdfa()
                    if status == .authorized {
                        onAuthorized()
                    } else {
                        showSettingsAlert(from: presenter)
                    }
                }
            }
        case .denied, .restricted:
            showSettingsAlert(from: presenter)
        @unknown default:
            onAuthorized()
        }
    }

    /// 거부 상태에서 사유를 설명하고 설정 앱으로 유도하는 알럿.
    @MainActor
    private static func showSettingsAlert(from presenter: UIViewController) {
        let alert = UIAlertController(
            title: "추적 허용이 필요해요",
            message: "오퍼월 리워드를 적립하려면 기기의 광고 식별자(IDFA) 추적 허용이 필요합니다.\n설정 > 추적에서 ‘이 앱이 추적하도록 허용’을 켜 주세요.",
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: "설정으로 이동", style: .default) { _ in
            if let url = URL(string: UIApplication.openSettingsURLString) {
                UIApplication.shared.open(url)
            }
        })
        alert.addAction(UIAlertAction(title: "취소", style: .cancel))
        presenter.present(alert, animated: true)
    }

    /// 테스트 단말기(TNK 콘솔) 등록용 IDFA 로그 — 허용 시 실제 값, 미허용 시 전부 0.
    private static func logIdfa() {
        let status = ATTrackingManager.trackingAuthorizationStatus.rawValue
        let idfa = ASIdentifierManager.shared().advertisingIdentifier.uuidString
        print("📱 [ATT] status=\(status) IDFA=\(idfa)")
    }
}
