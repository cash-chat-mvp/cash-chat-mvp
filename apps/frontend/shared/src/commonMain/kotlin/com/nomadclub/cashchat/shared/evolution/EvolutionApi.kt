package com.nomadclub.cashchat.shared.evolution

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
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
    val currentExp: Long? = null, // BE 미배포 시 null → UI 미표시. 배포 시 자동 노출(전방 호환).
)

@Serializable
data class EvolutionAttemptDto(
    val success: Boolean,
    val fromLevel: Int,
    val resultLevel: Int,
    val cost: Long,
    val timingGrade: TimingGrade? = null,
    val timingBonusRate: Double? = null,
    val baseSuccessRate: Double? = null,
    val finalSuccessRate: Double? = null,
)

@Serializable
data class EvolutionAttemptRecordDto(
    val success: Boolean,
    val fromLevel: Int,
    val resultLevel: Int,
    val cost: Long,
    val attemptedAt: String,
)

@Serializable
data class EvolutionAttemptsDto(val attempts: List<EvolutionAttemptRecordDto>)

@Serializable
data class TimingSessionDto(
    val sessionId: String,
    val serverStartedAt: String,
    val minimumHoldMs: Long,
    val cycleDurationMs: Long,
)

@Serializable
data class TimingAttempt(
    val sessionId: String,
    val releasedAtMs: Long,
)

@Serializable
private data class LegacyEvolutionAttemptRequest(
    val idempotencyKey: String,
)

@Serializable
private data class TimingEvolutionAttemptRequest(
    val idempotencyKey: String,
    val timing: TimingAttempt,
)

class EvolutionApi(private val client: HttpClient, private val baseUrl: String) {
    @Throws(Exception::class)
    suspend fun getState(): EvolutionStateDto = client.get("$baseUrl/api/evolution/me").body()

    @Throws(Exception::class)
    suspend fun createTimingSession(): TimingSessionDto =
        client.post("$baseUrl/api/evolution/timing-sessions").body()

    /** 버튼 1탭 = 새 idempotencyKey. 같은 탭의 네트워크 재시도는 같은 키 재사용(서버 멱등). */
    @Throws(Exception::class)
    suspend fun attempt(idempotencyKey: String, timing: TimingAttempt? = null): EvolutionAttemptDto =
        client.post("$baseUrl/api/evolution/attempt") {
            contentType(ContentType.Application.Json)
            setBody(
                if (timing == null) LegacyEvolutionAttemptRequest(idempotencyKey)
                else TimingEvolutionAttemptRequest(idempotencyKey, timing)
            )
        }.body()

    /** P3-1 — FeatureFlags.EVOLUTION_HISTORY 활성 전 호출 금지. */
    @Throws(Exception::class)
    suspend fun getAttempts(limit: Int = 20): EvolutionAttemptsDto =
        client.get("$baseUrl/api/evolution/attempts") { parameter("limit", limit) }.body()
}
