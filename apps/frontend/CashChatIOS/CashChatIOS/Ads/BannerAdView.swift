import SwiftUI
import GoogleMobileAds
import UIKit

/// AdMob 적응형 배너. 로드 실패 시 숨겨 레이아웃을 보존한다.
/// Android BannerAd Composable 과 동형(slot: chat_top / benefit_top).
struct BannerAdView: UIViewRepresentable {
    let slotName: String

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> BannerView {
        let width = UIScreen.main.bounds.width
        let banner = BannerView(adSize: currentOrientationAnchoredAdaptiveBanner(width: width))
        banner.adUnitID = AppConfig.admobBannerAdUnitId
        banner.delegate = context.coordinator
        if let root = Self.rootViewController() {
            banner.rootViewController = root
        }
        banner.load(Request())
        return banner
    }

    func updateUIView(_ uiView: BannerView, context: Context) {}

    private static func rootViewController() -> UIViewController? {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { $0.isKeyWindow }?.rootViewController
    }

    final class Coordinator: NSObject, BannerViewDelegate {
        func bannerView(_ bannerView: BannerView, didFailToReceiveAdWithError error: Error) {
            print("배너 로드 실패: \(error.localizedDescription)")
            bannerView.isHidden = true
        }
    }
}
