import AppTrackingTransparency
import AdSupport
import UIKit

/// App Tracking Transparency(ATT) 권한 요청 헬퍼.
///
/// iOS 14+ 에서는 ATT 허용을 받기 전까지 IDFA 가 전부 0(`00000000-...`)으로 반환된다.
/// TNK 오퍼월은 이 IDFA 로 단말기를 식별해 (테스트) 오퍼를 내려주므로,
/// ATT 허용을 받지 못하면 오퍼월 컨텐츠가 비어 보인다.
enum TrackingAuthorization {
    private static var requested = false

    /// ATT 권한을 앱 생명주기당 1회 요청한다.
    /// 시스템 프롬프트는 앱이 active 상태일 때만 표시되므로, 활성 직후 약간 지연해 호출한다.
    @MainActor
    static func requestIfNeeded() {
        guard !requested else { return }
        requested = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
            ATTrackingManager.requestTrackingAuthorization { status in
                // 테스트 단말기(TNK 콘솔) 등록용 — 허용 시 실제 IDFA, 거부/미허용 시 전부 0.
                let idfa = ASIdentifierManager.shared().advertisingIdentifier.uuidString
                print("📱 [ATT] status=\(status.rawValue) IDFA=\(idfa)")
            }
        }
    }
}
