import UIKit
import CashChatShared
import TnkRwdSdk2

/// TNK 오퍼월 노출 오케스트레이션.
/// 1) BE 에서 불투명 사용자 토큰 발급 → 2) TNK SDK setUserName → 3) AdOfferwallViewController 전체화면 present.
/// 토큰 발급 실패 시 오퍼월을 띄우지 않는다(잘못된 사용자로 적립되는 사고 방지).
@MainActor
enum TnkOfferwallManager {
    private static let offerwallApi = KoinHelper().offerwallApi()

    static func present(from presenter: UIViewController) {
        // ATT 미허용이면 IDFA 가 0 이라 오퍼가 비어 보인다.
        // 허용된 경우에만 오퍼월을 열고, 거부 상태면 설정 이동 안내 알럿을 띄운다.
        TrackingAuthorization.ensureAuthorized(from: presenter) {
            Task { @MainActor in
                do {
                    let dto = try await offerwallApi.issueUserToken()
                    TnkSession.sharedInstance()?.setUserName(dto.token)
                    let vc = AdOfferwallViewController()
                    vc.title = "오퍼월"
                    let nav = UINavigationController(rootViewController: vc)
                    nav.modalPresentationStyle = .fullScreen
                    presenter.present(nav, animated: true)
                } catch {
                    print("TnkOfferwall: 오퍼월 진입 실패 - \(error)")
                }
            }
        }
    }

    /// SwiftUI 에서 UIKit present 를 위해 현재 최상위 ViewController 를 찾는다.
    static func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes
            .first { $0.activationState == .foregroundActive } as? UIWindowScene
        var top = scene?.keyWindow?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}
