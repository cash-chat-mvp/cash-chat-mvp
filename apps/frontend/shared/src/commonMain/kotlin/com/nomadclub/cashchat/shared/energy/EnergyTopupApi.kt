package com.nomadclub.cashchat.shared.energy

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

@Serializable
data class EnergyTopupDto(val energy: Int, val maxEnergy: Int, val costPoints: Long, val pointBalance: Long)

@Serializable
private data class TopupRequest(val idempotencyKey: String)

/** P1-2 요청 API. FeatureFlags.POINT_TOPUP 활성 전까지 호출 금지. */
class EnergyTopupApi(private val client: HttpClient, private val baseUrl: String) {
    @Throws(Exception::class)
    suspend fun topup(idempotencyKey: String): EnergyTopupDto =
        client.post("$baseUrl/api/energy/topup") {
            contentType(ContentType.Application.Json)
            setBody(TopupRequest(idempotencyKey))
        }.body()
}
