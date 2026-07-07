package com.nomadclub.cashchat.ads

import android.app.Activity
import android.content.Context

/** 보상형 광고 표시 seam. 프로덕션은 [RewardedAdManager](AdMob), 테스트는 Fake. */
interface RewardedAdPresenter {
    fun preload(context: Context)
    fun isReady(): Boolean
    fun show(
        activity: Activity,
        nonce: String? = null,
        onRewarded: (amount: Int) -> Unit,
        onDismissed: () -> Unit,
        onNotReady: () -> Unit = {},
    )
}
