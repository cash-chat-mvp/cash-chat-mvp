package com.nomadclub.cashchat.feature.chat.evolution

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nomadclub.cashchat.shared.core.config.FeatureFlags
import com.nomadclub.cashchat.shared.core.network.ApiException
import com.nomadclub.cashchat.shared.evolution.EvolutionAttemptDto
import com.nomadclub.cashchat.shared.evolution.EvolutionStateDto
import com.nomadclub.cashchat.shared.evolution.EvolutionStore
import com.nomadclub.cashchat.shared.evolution.TimingAttempt
import com.nomadclub.cashchat.shared.evolution.TimingCapability
import com.nomadclub.cashchat.shared.evolution.TimingGrade
import com.nomadclub.cashchat.shared.evolution.TimingWindow
import com.nomadclub.cashchat.shared.evolution.localTimingGrade
import com.nomadclub.cashchat.shared.hud.HudStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 캐릭터 진화 — 미래형 분석 장치 UI 상태머신.
 * 로딩 → (capability 감지) → 콘텐츠. 길게 누르기 타이밍 보너스는 서버 지원 시에만 노출하고
 * 미지원/오류 시 기존 기본 확률 시도로 폴백한다.
 */
class EvolutionViewModel(
    val evolutionStore: EvolutionStore,
    private val hudStore: HudStore,
) : ViewModel() {

    /** IDLE → CHARGING(누르는 중) → RESOLVING(서버 요청) → RESULT */
    enum class Phase { IDLE, CHARGING, RESOLVING, RESULT }

    sealed interface UiState {
        data object Loading : UiState
        data class LoadError(val message: String) : UiState
        data class Content(
            val evolution: EvolutionStateDto,
            val capability: TimingCapability = TimingCapability.UNKNOWN,
            val phase: Phase = Phase.IDLE,
            val timingPosition: Float = 0f,
            val predictedGrade: TimingGrade? = null,
            val result: EvolutionAttemptDto? = null,
        ) : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private var holdStartedAt = 0L
    private var tickerJob: Job? = null

    init {
        loadInitial()
    }

    fun retryLoad() = loadInitial()

    private fun loadInitial() {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            val state = try {
                evolutionStore.refresh()
            } catch (e: Exception) {
                _uiState.value = UiState.LoadError("진화 정보를 불러오지 못했어요. 다시 시도해주세요")
                return@launch
            }
            _uiState.value = UiState.Content(evolution = state)
            // capability 감지·히스토리는 콘텐츠 표시를 막지 않도록 병렬 처리
            launch {
                val capability = runCatching { evolutionStore.detectTimingCapability() }
                    .getOrDefault(TimingCapability.UNSUPPORTED)
                updateContent { it.copy(capability = capability) }
            }
            if (FeatureFlags.EVOLUTION_HISTORY) launch { runCatching { evolutionStore.refreshHistory() } }
        }
    }

    private fun currentWindow(): TimingWindow? =
        evolutionStore.timingSession.value?.let {
            TimingWindow(minimumHoldMs = it.minimumHoldMs, cycleDurationMs = it.cycleDurationMs)
        }

    private inline fun updateContent(transform: (UiState.Content) -> UiState.Content) {
        (_uiState.value as? UiState.Content)?.let { _uiState.value = transform(it) }
    }

    // ── 길게 누르기 타이밍 ────────────────────────────────────────────────

    fun beginHold() {
        val content = _uiState.value as? UiState.Content ?: return
        if (content.phase != Phase.IDLE || content.capability != TimingCapability.SUPPORTED) return
        val window = currentWindow() ?: return
        holdStartedAt = SystemClock.elapsedRealtime()
        updateContent { it.copy(phase = Phase.CHARGING, timingPosition = 0f, predictedGrade = TimingGrade.NORMAL) }
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while ((_uiState.value as? UiState.Content)?.phase == Phase.CHARGING) {
                val elapsed = SystemClock.elapsedRealtime() - holdStartedAt
                val position = positionFor(elapsed, window)
                updateContent {
                    it.copy(timingPosition = position, predictedGrade = localTimingGrade(position, window))
                }
                delay(16)
            }
        }
    }

    fun cancelHold() {
        tickerJob?.cancel()
        updateContent { it.copy(phase = Phase.IDLE, timingPosition = 0f, predictedGrade = null) }
    }

    fun releaseHold() {
        val content = _uiState.value as? UiState.Content ?: return
        if (content.phase != Phase.CHARGING) return
        tickerJob?.cancel()
        val window = currentWindow()
        val elapsed = SystemClock.elapsedRealtime() - holdStartedAt
        // 0.6초 이전 해제는 취소 — 경험치를 소모하지 않는다.
        if (window == null || elapsed < window.minimumHoldMs) {
            cancelHold()
            return
        }
        val sessionId = evolutionStore.timingSession.value?.sessionId
        if (sessionId == null) {
            cancelHold()
            return
        }
        runAttempt(TimingAttempt(sessionId = sessionId, releasedAtMs = elapsed))
    }

    /** 타이밍 미지원(폴백) — 기존 기본 확률 시도 */
    fun attemptLegacy() {
        val content = _uiState.value as? UiState.Content ?: return
        if (content.phase != Phase.IDLE) return
        runAttempt(null)
    }

    private fun runAttempt(timing: TimingAttempt?) {
        updateContent { it.copy(phase = Phase.RESOLVING) }
        viewModelScope.launch {
            val result = try {
                evolutionStore.attempt(timing)
            } catch (e: ApiException) {
                updateContent { it.copy(phase = Phase.IDLE, timingPosition = 0f, predictedGrade = null) }
                _errorMessage.value = when (e.code) {
                    ApiException.INSUFFICIENT_EVOLUTION_EXP,
                    ApiException.INSUFFICIENT_POINTS -> "경험치가 부족해요. 채팅으로 모아볼까요?"
                    ApiException.ALREADY_MAX_LEVEL -> "이미 최고 레벨이에요!"
                    else -> e.message
                }
                runCatching { evolutionStore.refresh() }.getOrNull()?.let { refreshed ->
                    updateContent { it.copy(evolution = refreshed) }
                }
                return@launch
            } catch (e: Exception) {
                updateContent { it.copy(phase = Phase.IDLE, timingPosition = 0f, predictedGrade = null) }
                _errorMessage.value = "네트워크 오류 — 다시 시도해주세요"
                return@launch
            }
            updateContent { it.copy(phase = Phase.RESULT, result = result) }
            // 성공/실패 결과를 상태·HUD에 반영
            runCatching { evolutionStore.refresh() }.getOrNull()?.let { refreshed ->
                updateContent { it.copy(evolution = refreshed) }
            }
            hudStore.refresh()
        }
    }

    fun dismissResult() {
        updateContent { it.copy(phase = Phase.IDLE, result = null, timingPosition = 0f, predictedGrade = null) }
    }

    fun clearError() { _errorMessage.value = null }

    private fun positionFor(elapsedMs: Long, window: TimingWindow): Float {
        if (window.cycleDurationMs <= 0L) return 0f
        return (elapsedMs % window.cycleDurationMs).toFloat() / window.cycleDurationMs
    }
}
