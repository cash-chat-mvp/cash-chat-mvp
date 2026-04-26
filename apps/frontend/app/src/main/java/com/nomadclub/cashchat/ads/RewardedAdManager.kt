package com.nomadclub.cashchat.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.nomadclub.cashchat.config.AppConfig

/**
 * AdMob 보상형(Rewarded) 광고 사전 로드 및 노출 관리.
 *
 * 사용 패턴:
 *  1. 앱 진입 시 또는 채팅 시작 전 [preload] 호출 → 광고 미리 다운로드
 *  2. 노출 시점에 [show] 호출 → 광고 표시 + 콜백 수신
 *  3. [show] 완료 후 자동으로 다음 광고를 다시 [preload]
 */
class RewardedAdManager(
    private val appConfig: AppConfig
) {
    private var rewardedAd: RewardedAd? = null
    private var isLoading = false

    companion object {
        private const val TAG = "RewardedAdManager"
    }

    /**
     * 광고를 미리 로드합니다.
     * 이미 로드됐거나 로딩 중이면 무시합니다.
     */
    fun preload(context: Context) {
        if (rewardedAd != null || isLoading) return
        isLoading = true
        Log.d(TAG, "보상형 광고 사전 로드 시작: ${appConfig.admobRewardedAdUnitId}")

        RewardedAd.load(
            context,
            appConfig.admobRewardedAdUnitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d(TAG, "보상형 광고 로드 완료")
                    rewardedAd = ad
                    isLoading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "보상형 광고 로드 실패: ${error.message}")
                    rewardedAd = null
                    isLoading = false
                }
            }
        )
    }

    /**
     * 광고가 준비됐는지 여부를 반환합니다.
     */
    fun isReady(): Boolean = rewardedAd != null

    /**
     * 보상형 광고를 노출합니다.
     *
     * @param activity 광고를 표시할 Activity
     * @param onRewarded 광고 시청 완료 시 호출 (지급할 포인트 양 전달)
     * @param onDismissed 광고 닫힘 시 호출 (보상 미지급 포함, 항상 호출됨)
     * @param onNotReady 광고가 준비되지 않았을 때 호출
     */
    fun show(
        activity: Activity,
        onRewarded: (amount: Int) -> Unit,
        onDismissed: () -> Unit,
        onNotReady: () -> Unit = {}
    ) {
        val ad = rewardedAd
        if (ad == null) {
            Log.w(TAG, "광고가 준비되지 않음 — 사전 로드 필요")
            onNotReady()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "광고 닫힘")
                rewardedAd = null
                onDismissed()
                // 다음 광고 미리 로드
                preload(activity.applicationContext)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(TAG, "광고 노출 실패: ${error.message}")
                rewardedAd = null
                onDismissed()
                preload(activity.applicationContext)
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "광고 노출 시작")
                // show 직후 null 처리 → 중복 호출 방지
                rewardedAd = null
            }
        }

        ad.show(activity) { rewardItem ->
            Log.d(TAG, "보상 지급: ${rewardItem.amount} ${rewardItem.type}")
            onRewarded(rewardItem.amount)
        }
    }
}
