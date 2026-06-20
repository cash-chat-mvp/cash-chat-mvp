package com.nomadclub.cashchat.shared.wallet

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.Serializable

@Serializable
data class PointBalanceDto(val balance: Long)

/** P1-1 요청 API (docs/planning/be-api-requests-cc348.md). 배포 전까지 FeatureFlags.POINT_BALANCE로 차단. */
class PointsApi(private val client: HttpClient, private val baseUrl: String) {
    @Throws(Exception::class)
    suspend fun getBalance(): PointBalanceDto = client.get("$baseUrl/api/points/me").body()
}
