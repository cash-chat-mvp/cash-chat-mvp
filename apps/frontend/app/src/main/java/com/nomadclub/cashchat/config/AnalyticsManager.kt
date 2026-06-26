package com.nomadclub.cashchat.config

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Firebase Analytics 래퍼 (Epic C-1 핵심 이벤트).
 * iOS `AnalyticsManager.swift` 와 이벤트명/파라미터를 1:1로 맞춘다.
 */
class AnalyticsManager(context: Context) {
    private val analytics = FirebaseAnalytics.getInstance(context.applicationContext)

    fun logChatStart(sessionId: String, model: String) = log("chat_start") {
        putString("session_id", sessionId)
        putString("model", model)
    }

    fun logChatEnd(sessionId: String, messageCount: Int) = log("chat_end") {
        putString("session_id", sessionId)
        putLong("message_count", messageCount.toLong())
    }

    fun logAdView(adType: String, adUnitId: String) = log("ad_view") {
        putString("ad_type", adType)
        putString("ad_unit_id", adUnitId)
    }

    fun logAdFailed(adType: String, errorCode: Int) = log("ad_failed") {
        putString("ad_type", adType)
        putLong("error_code", errorCode.toLong())
    }

    fun logRewardEarned(rewardType: String, amount: Long) = log("reward_earned") {
        putString("reward_type", rewardType)
        putLong("amount", amount)
    }

    fun logChatBlocked(reason: String) = log("chat_blocked") {
        putString("reason", reason)
    }

    private inline fun log(name: String, block: Bundle.() -> Unit = {}) {
        analytics.logEvent(name, Bundle().apply(block))
    }
}
