package com.nomadclub.cashchat.shared.evolution

import com.nomadclub.cashchat.shared.platform.currentTimeMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

/** 진화 상태 + 시도 (스펙 §3.3). 버튼 1탭 = 새 idempotencyKey, 재시도는 같은 키. */
enum class TimingCapability { UNKNOWN, SUPPORTED, UNSUPPORTED }

class EvolutionStore private constructor(private val api: EvolutionGateway) {

    constructor(api: EvolutionApi) : this(api as EvolutionGateway)

    companion object {
        internal fun fromGateway(api: EvolutionGateway): EvolutionStore = EvolutionStore(api)
    }

    private val _state = MutableStateFlow<EvolutionStateDto?>(null)
    val state: StateFlow<EvolutionStateDto?> = _state.asStateFlow()

    private val _timingCapability = MutableStateFlow(TimingCapability.UNKNOWN)
    val timingCapability: StateFlow<TimingCapability> = _timingCapability.asStateFlow()

    /** SUPPORTED 감지 시 발급된 타이밍 세션. UI가 게이지 윈도우·시도 payload 구성에 사용. */
    private val _timingSession = MutableStateFlow<TimingSessionDto?>(null)
    val timingSession: StateFlow<TimingSessionDto?> = _timingSession.asStateFlow()

    private var currentAttemptKey: String? = null
    private var currentAttemptTiming: TimingAttempt? = null

    private val _history = MutableStateFlow<List<EvolutionAttemptRecordDto>>(emptyList())
    val history: StateFlow<List<EvolutionAttemptRecordDto>> = _history.asStateFlow()

    @Throws(Exception::class)
    suspend fun refresh(): EvolutionStateDto = api.getState().also { _state.value = it }

    @Throws(Exception::class)
    suspend fun detectTimingCapability(): TimingCapability =
        runCatching { api.createTimingSession() }
            .fold(
                onSuccess = { session ->
                    _timingSession.value = session
                    TimingCapability.SUPPORTED
                },
                onFailure = {
                    _timingSession.value = null
                    TimingCapability.UNSUPPORTED
                },
            ).also { _timingCapability.value = it }

    /** P3-1 — FeatureFlags.EVOLUTION_HISTORY 활성 시에만 호출. */
    @Throws(Exception::class)
    suspend fun refreshHistory() {
        _history.value = api.getAttempts().attempts
    }

    /** 새 시도 시작 — 새 idempotencyKey 발급 후 호출. */
    @Throws(Exception::class)
    suspend fun attempt(timing: TimingAttempt? = null): EvolutionAttemptDto {
        val key = newUuidLike().also { currentAttemptKey = it }
        val requestTiming = timing?.takeIf {
            _timingCapability.value == TimingCapability.SUPPORTED &&
                _timingSession.value?.sessionId == it.sessionId
        }
        currentAttemptTiming = requestTiming
        return api.attempt(key, requestTiming)
    }

    /** 직전 시도의 네트워크 재시도 — 같은 키 재사용(서버 멱등 보장). */
    @Throws(Exception::class)
    suspend fun retryLastAttempt(): EvolutionAttemptDto {
        val key = currentAttemptKey ?: return attempt()
        return api.attempt(key, currentAttemptTiming)
    }

    /** 로그아웃/세션 종료 시 다음 사용자에게 이전 진화 상태·기록이 노출되지 않도록 초기화한다. */
    fun reset() {
        _state.value = null
        _history.value = emptyList()
        _timingCapability.value = TimingCapability.UNKNOWN
        _timingSession.value = null
        currentAttemptKey = null
        currentAttemptTiming = null
    }

    // commonMain에는 UUID API가 없어 시간+난수 조합으로 충분한 유일성 확보(서버 max 255자)
    private fun newUuidLike(): String =
        "${currentTimeMillis()}-${Random.nextLong().toULong().toString(16)}-${Random.nextLong().toULong().toString(16)}"
}
