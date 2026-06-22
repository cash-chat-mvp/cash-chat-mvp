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
    modifier: Modifier = Modifier,
    nativeAdManager: NativeAdManager = koinInject(),
    appConfig: AppConfig = koinInject(),
) {
    if (!appConfig.adsEnabled) return

    val context = LocalContext.current
    var ad by remember { mutableStateOf<NativeAd?>(null) }

    DisposableEffect(Unit) {
        var disposed = false
        nativeAdManager.load(
            context = context,
            onLoaded = { loaded ->
                // 컴포저블이 이미 사라진 뒤 도착한 광고는 즉시 해제해 누수를 막는다.
                if (disposed) loaded.destroy() else ad = loaded
            },
            onFailed = { code -> Log.w("ChatNativeAdView", "네이티브 광고 로드 실패: $code") },
        )
        onDispose {
            disposed = true
            ad?.destroy()
        }
    }

    val current = ad ?: return

    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant.toArgb()
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    AndroidView(
        modifier = modifier.fillMaxWidth().padding(end = 48.dp),
        factory = { ctx ->
            val density = ctx.resources.displayMetrics.density
            fun dp(v: Int) = (v * density).toInt()

            val headline = TextView(ctx).apply { textSize = 14f; maxLines = 2; setTextColor(onSurfaceVariant) }
            val advertiser = TextView(ctx).apply { textSize = 11f; alpha = 0.7f; setTextColor(onSurfaceVariant) }
            val adBadge = TextView(ctx).apply {
                text = "Ad"; textSize = 10f
                setPadding(dp(4), dp(1), dp(4), dp(1))
                setTextColor(onSurfaceVariant)
            }
            val rating = RatingBar(ctx, null, android.R.attr.ratingBarStyleSmall).apply {
                numStars = 5; stepSize = 0.1f; isClickable = false
            }
            val mediaView = MediaView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(96),
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
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(surfaceVariant)
                    val r = dp(16).toFloat(); val s = dp(4).toFloat()
                    // 좌상, 우상, 우하, 좌하 (각 모서리 x,y 쌍)
                    cornerRadii = floatArrayOf(r, r, r, r, r, r, s, s)
                }
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
