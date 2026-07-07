package com.nomadclub.cashchat.mock

import android.app.Activity
import android.content.Context
import com.nomadclub.cashchat.ads.RewardedAdPresenter

/**
 * AdMob 대체. show() 즉시 보상 적립을 시뮬레이션한다:
 * usedToday++ → 다음 /api/ads/reward/quota 조회가 증가분 반영 →
 * AdRewardStore.awaitRewardApplied 가 즉시(attempt 0) APPLIED 판정.
 */
class FakeRewardedAdPresenter(private val state: MockBackendState) : RewardedAdPresenter {
    override fun preload(context: Context) { /* no-op */ }
    override fun isReady(): Boolean = true
    override fun show(
        activity: Activity,
        nonce: String?,
        onRewarded: (amount: Int) -> Unit,
        onDismissed: () -> Unit,
        onNotReady: () -> Unit,
    ) {
        state.usedToday += 1
        state.energy = state.maxEnergy   // refreshEnergyOnly 가 충전 관측
        onRewarded(10)
        onDismissed()
    }
}
