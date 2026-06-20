package com.nomadclub.cashchat.shared.ads

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import kotlinx.serialization.Serializable

@Serializable
data class AdRewardQuotaDto(
    val usedToday: Int,
    val dailyLimit: Int,
    val remaining: Int,
    val resetAtKst: String,
)

@Serializable
data class IssueNonceDto(val nonce: String, val expiresAt: String)

class AdsApi(private val client: HttpClient, private val baseUrl: String) {
    @Throws(Exception::class)
    suspend fun getQuota(): AdRewardQuotaDto = client.get("$baseUrl/api/ads/reward/quota").body()

    @Throws(Exception::class)
    suspend fun issueNonce(): IssueNonceDto = client.post("$baseUrl/api/ads/reward/issue-nonce").body()
}
