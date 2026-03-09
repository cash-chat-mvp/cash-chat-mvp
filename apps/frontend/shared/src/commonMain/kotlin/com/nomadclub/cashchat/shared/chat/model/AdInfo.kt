package com.nomadclub.cashchat.shared.chat.model

data class AdInfo(
    val brand: String,
    val tagline: String,
    val cta: String,
    val emoji: String,
    val bg: Long,
    val accent: Long,
    val category: String = "프리미엄"
)
