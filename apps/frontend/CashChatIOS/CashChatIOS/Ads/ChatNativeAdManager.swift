import Foundation
import Combine
import GoogleMobileAds

/// 채팅 인라인 네이티브 광고 1회성 로딩 헬퍼.
/// ChatItemNativeAd placeholder 1개당 ChatNativeAdLoader 1개가 생성되어 광고를 받아온다.
final class ChatNativeAdLoader: NSObject, ObservableObject, NativeAdLoaderDelegate {
    @Published var nativeAd: NativeAd?

    private var adLoader: AdLoader?

    func load() {
        guard AppConfig.adsEnabled else { return }
        guard let root = Self.rootViewController() else { return }
        let loader = AdLoader(
            adUnitID: AppConfig.admobNativeAdUnitId,
            rootViewController: root,
            adTypes: [.native],
            options: nil
        )
        loader.delegate = self
        loader.load(Request())
        self.adLoader = loader
    }

    func adLoader(_ adLoader: AdLoader, didReceive nativeAd: NativeAd) {
        self.nativeAd = nativeAd
    }

    func adLoader(_ adLoader: AdLoader, didFailToReceiveAdWithError error: Error) {
        print("네이티브 광고 로드 실패: \(error.localizedDescription)")
    }

    private static func rootViewController() -> UIViewController? {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { $0.isKeyWindow }?.rootViewController
    }
}
