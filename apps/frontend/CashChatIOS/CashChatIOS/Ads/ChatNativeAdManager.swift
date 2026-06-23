import Foundation
import Combine
import GoogleMobileAds

/// adId별 로딩된 네이티브 광고 캐시(앱 전역 싱글톤).
///
/// SwiftUI List/ScrollView 안에서 스크롤로 뷰가 화면 밖으로 나갔다 돌아오면 `.onAppear`가
/// 다시 발화한다. 그때마다 광고를 새로 로딩하면 과도한 광고 요청 → AdMob 정책 위반 위험이
/// 있으므로, ChatItemNativeAd의 고유 id별로 로딩된 광고를 캐시해 재사용한다.
/// 채팅 화면을 떠날 때 ``clear()``로 비운다.
final class ChatNativeAdCache {
    static let shared = ChatNativeAdCache()
    private init() {}

    private var cache: [String: NativeAd] = [:]

    func ad(for id: String) -> NativeAd? { cache[id] }
    func store(_ ad: NativeAd, for id: String) { cache[id] = ad }
    func clear() { cache.removeAll() }
}

/// 채팅 인라인 네이티브 광고 로딩 헬퍼.
/// ChatItemNativeAd placeholder(고유 id)당 1회만 로딩하고, 같은 id는 캐시에서 재사용한다.
final class ChatNativeAdLoader: NSObject, ObservableObject, NativeAdLoaderDelegate {
    @Published var nativeAd: NativeAd?

    private var adLoader: AdLoader?
    private var adId: String = ""

    func load(adId: String) {
        self.adId = adId
        // 캐시 적중 시 재로딩하지 않고 즉시 재사용(AdMob 정책 위반 방지).
        if let cached = ChatNativeAdCache.shared.ad(for: adId) {
            nativeAd = cached
            return
        }
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
        ChatNativeAdCache.shared.store(nativeAd, for: adId)
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
