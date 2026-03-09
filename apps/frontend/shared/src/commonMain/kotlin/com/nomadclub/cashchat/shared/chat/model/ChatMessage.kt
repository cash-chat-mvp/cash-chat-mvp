package com.nomadclub.cashchat.shared.chat.model

import com.nomadclub.cashchat.shared.platform.currentTimeMillis

sealed class ChatMessage {
    abstract val id: String
    abstract val timestamp: Long

    data class Text(
        override val id: String,
        val text: String,
        val isUser: Boolean,
        override val timestamp: Long = currentTimeMillis()
    ) : ChatMessage()

    data class InlineAd(
        override val id: String,
        val ad: AdInfo,
        override val timestamp: Long = currentTimeMillis()
    ) : ChatMessage()

    data class RewardPrompt(
        override val id: String,
        override val timestamp: Long = currentTimeMillis()
    ) : ChatMessage()
}
