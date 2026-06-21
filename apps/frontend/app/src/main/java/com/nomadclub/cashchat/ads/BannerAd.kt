package com.nomadclub.cashchat.ads

import android.util.Log
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.nomadclub.cashchat.config.AppConfig
import com.nomadclub.cashchat.shared.ads.BannerAdSlot
import org.koin.compose.koinInject

/**
 * AdMob 적응형(anchored adaptive) 배너.
 * - 로드 실패 시 슬롯을 숨긴다(높이 0) → 레이아웃 깨짐 방지.
 * - AdView 는 remember 로 보존하고 onDispose 에서 destroy 한다.
 */
@Composable
fun BannerAd(
    slot: BannerAdSlot,
    modifier: Modifier = Modifier,
    appConfig: AppConfig = koinInject(),
) {
    if (!slot.isEnabled()) return

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val widthDp = configuration.screenWidthDp

    var visible by remember { mutableStateOf(true) }
    if (!visible) return

    val adView = remember(slot) {
        AdView(context).apply {
            adUnitId = appConfig.admobBannerAdUnitId
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp))
            adListener = object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w("BannerAd", "배너 로드 실패(${slot.analyticsName}): ${error.message}")
                    visible = false
                }
            }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            )
            loadAd(AdRequest.Builder().build())
        }
    }

    DisposableEffect(adView) {
        onDispose { adView.destroy() }
    }

    AndroidView(
        factory = { adView },
        modifier = modifier.fillMaxWidth(),
    )
}
