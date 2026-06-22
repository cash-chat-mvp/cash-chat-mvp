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
 * AdMob 네이티브 광고 1회성 로딩 헬퍼.
 * 채팅 리스트의 ChatItem.NativeAd placeholder 1개당 1회 호출해 광고를 받아온다.
 */
class NativeAdManager(
    private val appConfig: AppConfig,
) {
    companion object { private const val TAG = "NativeAdManager" }

    /**
     * 네이티브 광고를 로딩한다.
     * @param onLoaded 성공 시 NativeAd 전달. 호출자는 화면에서 사라질 때 [NativeAd.destroy] 책임.
     * @param onFailed 실패 시 errorCode 전달(빈 자리 처리 + 로깅용).
     */
    fun load(
        context: Context,
        onLoaded: (NativeAd) -> Unit,
        onFailed: (Int) -> Unit,
    ) {
        val loader = AdLoader.Builder(context, appConfig.admobNativeAdUnitId)
            .forNativeAd { ad -> onLoaded(ad) }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "네이티브 광고 로드 실패: ${error.message}")
                    onFailed(error.code)
                }
            })
            .build()
        loader.loadAd(AdRequest.Builder().build())
    }
}
