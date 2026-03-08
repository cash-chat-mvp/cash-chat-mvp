package com.nomadclub.cashchat.feature.chat.models

sealed class ChatMessage {
    abstract val id: String
    abstract val timestamp: Long

    data class Text(
        override val id: String,
        val text: String,
        val isUser: Boolean,
        override val timestamp: Long = System.currentTimeMillis()
    ) : ChatMessage()

    data class InlineAd(
        override val id: String,
        val ad: AdInfo,
        override val timestamp: Long = System.currentTimeMillis()
    ) : ChatMessage()

    data class RewardPrompt(
        override val id: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : ChatMessage()
}

data class AdInfo(
    val brand: String,
    val tagline: String,
    val cta: String,
    val emoji: String,
    val bg: Long,
    val accent: Long,
    val category: String = "프리미엄"
)
