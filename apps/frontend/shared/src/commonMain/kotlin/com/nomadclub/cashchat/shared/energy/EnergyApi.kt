package com.nomadclub.cashchat.shared.energy

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.Serializable

@Serializable
data class EnergyDto(
    val energy: Int,
    val maxEnergy: Int,
    val nextRecoverAt: String? = null,   // P1-3 확장 — 배포 전엔 항상 null
    val recoverAmount: Int? = null,
)

class EnergyApi(private val client: HttpClient, private val baseUrl: String) {
    @Throws(Exception::class)
    suspend fun getMyEnergy(): EnergyDto = client.get("$baseUrl/api/energy/me").body()
}
