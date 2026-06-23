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
    // 로딩 진행 중인 adId. 첫 요청 완료 전 .onAppear가 다시 발화해도 중복 요청을 막는다.
    private var loadingIds: Set<String> = []

    func ad(for id: String) -> NativeAd? { cache[id] }
    func store(_ ad: NativeAd, for id: String) { cache[id] = ad }

    /// 로딩을 시작해도 되는지 여부. 캐시에 있거나 이미 로딩 중이면 false(중복 요청 차단).
    func beginLoading(_ id: String) -> Bool {
        if cache[id] != nil || loadingIds.contains(id) { return false }
        loadingIds.insert(id)
        return true
    }
    func finishLoading(_ id: String) { loadingIds.remove(id) }
    func clear() { cache.removeAll(); loadingIds.removeAll() }
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
        // 첫 요청 완료 전 .onAppear가 다시 발화해도 중복 네트워크 요청을 막는다.
        guard ChatNativeAdCache.shared.beginLoading(adId) else { return }
        guard AppConfig.adsEnabled else { ChatNativeAdCache.shared.finishLoading(adId); return }
        guard let root = Self.rootViewController() else { ChatNativeAdCache.shared.finishLoading(adId); return }
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
        ChatNativeAdCache.shared.finishLoading(adId)
        self.nativeAd = nativeAd
    }

    func adLoader(_ adLoader: AdLoader, didFailToReceiveAdWithError error: Error) {
        ChatNativeAdCache.shared.finishLoading(adId)
        print("네이티브 광고 로드 실패: \(error.localizedDescription)")
    }

    private static func rootViewController() -> UIViewController? {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { $0.isKeyWindow }?.rootViewController
    }
}
