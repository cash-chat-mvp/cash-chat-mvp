import Foundation
import GoogleMobileAds
import UIKit

/// AdMob 보상형 광고 사전 로드/노출 관리. Android RewardedAdManager와 동형.
/// SSV(서버 검증)용 nonce는 customData로 전달한다.
@MainActor
final class RewardedAdManager: NSObject {
    private var rewardedAd: RewardedAd?
    private var isLoading = false
    private let adUnitId = AppConfig.admobRewardedAdUnitId

    private var onRewarded: ((Int) -> Void)?
    private var onDismissed: (() -> Void)?

    /// 광고 미리 로드. 이미 로드됐거나 로딩 중이면 무시.
    func preload() {
        guard rewardedAd == nil, !isLoading else { return }
        isLoading = true
        RewardedAd.load(with: adUnitId, request: Request()) { [weak self] ad, error in
            guard let self else { return }
            self.isLoading = false
            if let error {
                print("리워드 광고 로드 실패: \(error.localizedDescription)")
                self.rewardedAd = nil
                return
            }
            self.rewardedAd = ad
        }
    }

    var isReady: Bool { rewardedAd != nil }

    /// 광고 노출. 준비 안 됐으면 onNotReady. 닫힘 시 항상 onDismissed.
    func show(
        nonce: String?,
        onRewarded: @escaping (Int) -> Void,
        onDismissed: @escaping () -> Void,
        onNotReady: @escaping () -> Void = {}
    ) {
        guard let ad = rewardedAd else {
            // 초기 preload 가 (SDK 초기화 타이밍/네트워크 등으로) 실패해 nil 인 경우,
            // 다음 시도를 위해 즉시 재요청을 걸어 자가 복구한다.
            preload()
            onNotReady()
            return
        }
        if let nonce {
            // customRewardText 가 SSV 콜백의 custom_data 파라미터로 전달된다(서버 nonce 검증용).
            let options = ServerSideVerificationOptions()
            options.customRewardText = nonce
            ad.serverSideVerificationOptions = options
        }
        self.onRewarded = onRewarded
        self.onDismissed = onDismissed
        ad.fullScreenContentDelegate = self

        guard let root = Self.topViewController() else { onDismissed(); return }
        rewardedAd = nil // 중복 노출 방지
        ad.present(from: root) { [weak self] in
            let amount = ad.adReward.amount.intValue
            self?.onRewarded?(amount)
        }
    }

    private static func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
        var top = scene?.windows.first(where: { $0.isKeyWindow })?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}

extension RewardedAdManager: FullScreenContentDelegate {
    func adDidDismissFullScreenContent(_ ad: FullScreenPresentingAd) {
        onDismissed?()
        onDismissed = nil
        onRewarded = nil
        preload()
    }

    func ad(_ ad: FullScreenPresentingAd, didFailToPresentFullScreenContentWithError error: Error) {
        print("리워드 광고 노출 실패: \(error.localizedDescription)")
        onDismissed?()
        onDismissed = nil
        onRewarded = nil
        preload()
    }
}
