package com.nomadclub.cashchat.ads

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.nomadclub.cashchat.config.AppConfig

/**
 * AdMob 네이티브 광고 로딩·캐시 헬퍼(앱 싱글톤).
 *
 * 채팅 리스트의 ChatItem.NativeAd placeholder는 고유 [adId]를 가진다. LazyColumn 안에서
 * 스크롤로 화면을 벗어났다 돌아오면 Composable이 재생성되는데, 그때마다 새 광고를 로딩하면
 * 과도한 광고 요청 → AdMob 정책 위반/계정 정지 위험이 있다. 그래서 adId별로 로딩된 광고를
 * 캐시해 두고 재진입 시 같은 광고를 재사용한다. 채팅 화면을 떠날 때 [clear]로 일괄 해제한다.
 */
class NativeAdManager(
    private val appConfig: AppConfig,
) {
    companion object { private const val TAG = "NativeAdManager" }

    // adId → 로딩 완료된 광고. (모든 접근은 메인 스레드 — AdMob 콜백/Compose 모두 메인)
    private val cache = mutableMapOf<String, NativeAd>()
    // adId → 로딩 진행 중 대기 콜백. 동일 adId 동시 요청을 1회로 합쳐 중복 요청을 막는다.
    private val pending = mutableMapOf<String, PendingCallbacks>()
    // adId → 진행 중인 AdLoader. 로딩 완료 전 GC되면 콜백이 영영 안 오므로 완료/실패까지 강참조로 유지한다.
    private val loaders = mutableMapOf<String, AdLoader>()

    private class PendingCallbacks {
        val onLoaded = mutableListOf<(NativeAd) -> Unit>()
        val onFailed = mutableListOf<(Int) -> Unit>()
    }

    /**
     * [adId] 광고를 로딩하거나 캐시된 광고를 반환한다.
     * @param onLoaded 성공 시(또는 캐시 적중 시) NativeAd 전달. 해제 책임은 [NativeAdManager]에 있다.
     * @param onFailed 실패 시 errorCode 전달(빈 자리 처리 + 로깅용).
     */
    fun load(
        adId: String,
        context: Context,
        onLoaded: (NativeAd) -> Unit,
        onFailed: (Int) -> Unit,
    ) {
        cache[adId]?.let { onLoaded(it); return }
        // 이미 로딩 중이면 콜백만 큐에 추가(중복 광고 요청 방지). 성공/실패 모두 대기자 전원에게 통지한다.
        pending[adId]?.let { it.onLoaded.add(onLoaded); it.onFailed.add(onFailed); return }
        pending[adId] = PendingCallbacks().also { it.onLoaded.add(onLoaded); it.onFailed.add(onFailed) }

        val loader = AdLoader.Builder(context, appConfig.admobNativeAdUnitId)
            .forNativeAd { ad ->
                cache[adId] = ad
                pending.remove(adId)?.onLoaded?.forEach { it(ad) }
                loaders.remove(adId)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "네이티브 광고 로드 실패: ${error.message}")
                    pending.remove(adId)?.onFailed?.forEach { it(error.code) }
                    loaders.remove(adId)
                }
            })
            .build()
        loaders[adId] = loader
        loader.loadAd(AdRequest.Builder().build())
    }

    /** 채팅 화면 이탈 시 호출 — 캐시된 광고를 모두 해제한다. */
    fun clear() {
        cache.values.forEach { it.destroy() }
        cache.clear()
        pending.clear()
        loaders.clear()
    }
}
