import Foundation
import FirebaseAnalytics

/// Firebase Analytics 래퍼 (Epic C-1 핵심 이벤트).
/// Android `AnalyticsManager.kt`와 이벤트명/파라미터를 1:1로 맞춘다.
enum AnalyticsManager {
    static func logChatStart(sessionId: String, model: String) {
        Analytics.logEvent("chat_start", parameters: [
            "session_id": sessionId,
            "model": model,
        ])
    }

    static func logChatEnd(sessionId: String, messageCount: Int) {
        Analytics.logEvent("chat_end", parameters: [
            "session_id": sessionId,
            "message_count": messageCount,
        ])
    }

    static func logAdView(adType: String, adUnitId: String) {
        Analytics.logEvent("ad_view", parameters: [
            "ad_type": adType,
            "ad_unit_id": adUnitId,
        ])
    }

    static func logAdFailed(adType: String, errorCode: Int) {
        Analytics.logEvent("ad_failed", parameters: [
            "ad_type": adType,
            "error_code": errorCode,
        ])
    }

    static func logRewardEarned(rewardType: String, amount: Int) {
        Analytics.logEvent("reward_earned", parameters: [
            "reward_type": rewardType,
            "amount": amount,
        ])
    }

    static func logChatBlocked(reason: String) {
        Analytics.logEvent("chat_blocked", parameters: [
            "reason": reason,
        ])
    }
}
