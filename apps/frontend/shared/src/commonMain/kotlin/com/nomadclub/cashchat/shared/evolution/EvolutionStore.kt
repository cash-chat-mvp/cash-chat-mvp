package com.nomadclub.cashchat.shared.evolution

import com.nomadclub.cashchat.shared.platform.currentTimeMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

/** 진화 상태 + 시도 (스펙 §3.3). 버튼 1탭 = 새 idempotencyKey, 재시도는 같은 키. */
class EvolutionStore(private val api: EvolutionApi) {

    private val _state = MutableStateFlow<EvolutionStateDto?>(null)
    val state: StateFlow<EvolutionStateDto?> = _state.asStateFlow()

    private var currentAttemptKey: String? = null

    @Throws(Exception::class)
    suspend fun refresh(): EvolutionStateDto = api.getState().also { _state.value = it }

    /** 새 시도 시작 — 새 idempotencyKey 발급 후 호출. */
    @Throws(Exception::class)
    suspend fun attempt(): EvolutionAttemptDto {
        val key = newUuidLike().also { currentAttemptKey = it }
        return api.attempt(key)
    }

    /** 직전 시도의 네트워크 재시도 — 같은 키 재사용(서버 멱등 보장). */
    @Throws(Exception::class)
    suspend fun retryLastAttempt(): EvolutionAttemptDto {
        val key = currentAttemptKey ?: return attempt()
        return api.attempt(key)
    }

    // commonMain에는 UUID API가 없어 시간+난수 조합으로 충분한 유일성 확보(서버 max 255자)
    private fun newUuidLike(): String =
        "${currentTimeMillis()}-${Random.nextLong().toULong().toString(16)}-${Random.nextLong().toULong().toString(16)}"
}
