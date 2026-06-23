package com.nomadclub.cashchat.ads

import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.nomadclub.cashchat.config.AppConfig
import org.koin.compose.koinInject

/**
 * 채팅 리스트에 메시지 버블처럼 삽입되는 네이티브 광고(시안 B).
 * 로딩 성공 시에만 렌더하고, 실패/로딩 전에는 빈 자리(아무것도 안 그림)로 둔다.
 */
@Composable
fun ChatNativeAdView(
    adId: String,
    modifier: Modifier = Modifier,
    nativeAdManager: NativeAdManager = koinInject(),
    appConfig: AppConfig = koinInject(),
) {
    if (!appConfig.adsEnabled) return

    val context = LocalContext.current
    var ad by remember(adId) { mutableStateOf<NativeAd?>(null) }

    // 광고 로딩/해제는 NativeAdManager(캐시)가 책임진다. 스크롤로 재진입해도 같은 adId면
    // 캐시된 광고를 즉시 반환하므로 재로딩하지 않는다(AdMob 정책 위반 방지).
    DisposableEffect(adId) {
        nativeAdManager.load(
            adId = adId,
            context = context,
            onLoaded = { loaded -> ad = loaded },
            onFailed = { code -> Log.w("ChatNativeAdView", "네이티브 광고 로드 실패: $code") },
        )
        onDispose { /* 광고 해제는 NativeAdManager.clear()에서 일괄 처리 */ }
    }

    val current = ad ?: return

    // 테마 색상은 Composable에서 읽어 update 블록으로 전달한다. 다크/라이트 모드가 바뀌면
    // recomposition으로 update 가 다시 돌아 색상이 실시간 갱신된다(factory 는 1회만 실행됨).
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant.toArgb()
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    AndroidView(
        modifier = modifier.fillMaxWidth().padding(end = 48.dp),
        factory = { ctx ->
            val density = ctx.resources.displayMetrics.density
            fun dp(v: Int) = (v * density).toInt()

            val headline = TextView(ctx).apply { tag = TAG_HEADLINE; textSize = 14f; maxLines = 2 }
            val advertiser = TextView(ctx).apply { tag = TAG_ADVERTISER; textSize = 11f; alpha = 0.7f }
            val adBadge = TextView(ctx).apply {
                tag = TAG_AD_BADGE
                text = "Ad"; textSize = 10f
                setPadding(dp(4), dp(1), dp(4), dp(1))
            }
            val rating = RatingBar(ctx, null, android.R.attr.ratingBarStyleSmall).apply {
                numStars = 5; stepSize = 0.1f; isClickable = false
            }
            val mediaView = MediaView(ctx).apply {
                // AdMob 권장 최소 미디어 크기는 120x120dp — 미만이면 영상 광고에서
                // "media too small" 경고가 뜬다. 너비는 MATCH_PARENT(버블 폭)라 충분.
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(120),
                )
            }
            val icon = ImageView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
            }
            val cta = Button(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }

            val topRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(icon)
                addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(8), 0, dp(8), 0)
                    addView(headline)
                    addView(advertiser)
                    addView(rating)
                })
                addView(adBadge)
            }

            val container = LinearLayout(ctx).apply {
                tag = TAG_CONTAINER
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                addView(topRow)
                addView(mediaView)
                addView(cta)
            }

            NativeAdView(ctx).apply {
                this.headlineView = headline
                this.advertiserView = advertiser
                this.starRatingView = rating
                this.mediaView = mediaView
                this.iconView = icon
                this.callToActionView = cta
                addView(container)
            }
        },
        update = { adView ->
            val density = adView.resources.displayMetrics.density
            fun dp(v: Int) = (v * density).toInt()

            // 테마 색상 적용(다크/라이트 전환 실시간 반영).
            adView.findViewWithTag<LinearLayout>(TAG_CONTAINER)?.background =
                android.graphics.drawable.GradientDrawable().apply {
                    setColor(surfaceVariant)
                    val r = dp(16).toFloat(); val s = dp(4).toFloat()
                    // 좌상, 우상, 우하, 좌하 (각 모서리 x,y 쌍)
                    cornerRadii = floatArrayOf(r, r, r, r, r, r, s, s)
                }
            adView.findViewWithTag<TextView>(TAG_HEADLINE)?.setTextColor(onSurfaceVariant)
            adView.findViewWithTag<TextView>(TAG_ADVERTISER)?.setTextColor(onSurfaceVariant)
            adView.findViewWithTag<TextView>(TAG_AD_BADGE)?.setTextColor(onSurfaceVariant)

            (adView.headlineView as TextView).text = current.headline
            (adView.advertiserView as TextView).apply {
                text = current.advertiser ?: current.store ?: ""
                visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
            }
            (adView.starRatingView as RatingBar).apply {
                val r = current.starRating
                if (r != null) { rating = r.toFloat(); visibility = View.VISIBLE } else visibility = View.GONE
            }
            (adView.callToActionView as Button).text = current.callToAction ?: "자세히 보기"
            (adView.iconView as ImageView).apply {
                val drawable = current.icon?.drawable
                if (drawable != null) { setImageDrawable(drawable); visibility = View.VISIBLE } else visibility = View.GONE
            }
            adView.setNativeAd(current)
        },
    )
}

private const val TAG_CONTAINER = "ad_container"
private const val TAG_HEADLINE = "ad_headline"
private const val TAG_ADVERTISER = "ad_advertiser"
private const val TAG_AD_BADGE = "ad_badge"
