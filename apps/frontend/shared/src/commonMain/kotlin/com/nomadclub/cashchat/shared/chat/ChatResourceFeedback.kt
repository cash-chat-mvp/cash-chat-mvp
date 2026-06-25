package com.nomadclub.cashchat.shared.chat

sealed interface ChatResourceFeedback {
    val eventId: Long
    val messageId: String

    data class EnergySpent(
        override val eventId: Long,
        override val messageId: String,
        val amount: Int = -1,
    ) : ChatResourceFeedback

    data class RewardEarned(
        override val eventId: Long,
        override val messageId: String,
        val pointDelta: Long = 1,
        val expDelta: Long = 1,
    ) : ChatResourceFeedback
}
