package com.wnl.cashchat.api.domain.ad.service

data class GoogleAdSsvCallback(
    val adUnit: String,
    val rewardAmount: Int,
    val rewardItem: String,
    val timestamp: Long,
    val transactionId: String,
    val userId: String,
    val customData: String? = null,
    val signature: String,
    val keyId: Long,
    val rawQueryString: String,
    val signedPayload: String,
)
