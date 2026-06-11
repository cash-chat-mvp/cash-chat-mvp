package com.nomadclub.cashchat.shared.evolution

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

@Serializable
data class EvolutionStateDto(
    val level: Int,
    val isMaxLevel: Boolean,
    val nextAttemptCost: Long? = null,
    val nextSuccessRate: Double? = null,
)

@Serializable
data class EvolutionAttemptDto(
    val success: Boolean,
    val fromLevel: Int,
    val resultLevel: Int,
    val cost: Long,
)

@Serializable
private data class EvolutionAttemptRequest(val idempotencyKey: String)

class EvolutionApi(private val client: HttpClient, private val baseUrl: String) {
    @Throws(Exception::class)
    suspend fun getState(): EvolutionStateDto = client.get("$baseUrl/api/evolution/me").body()

    /** 버튼 1탭 = 새 idempotencyKey. 같은 탭의 네트워크 재시도는 같은 키 재사용(서버 멱등). */
    @Throws(Exception::class)
    suspend fun attempt(idempotencyKey: String): EvolutionAttemptDto =
        client.post("$baseUrl/api/evolution/attempt") {
            contentType(ContentType.Application.Json)
            setBody(EvolutionAttemptRequest(idempotencyKey))
        }.body()
}
