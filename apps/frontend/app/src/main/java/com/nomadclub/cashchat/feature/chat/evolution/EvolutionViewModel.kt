package com.nomadclub.cashchat.feature.chat.evolution

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nomadclub.cashchat.shared.core.config.FeatureFlags
import com.nomadclub.cashchat.shared.core.network.ApiException
import com.nomadclub.cashchat.shared.evolution.EvolutionAttemptDto
import com.nomadclub.cashchat.shared.evolution.EvolutionStore
import com.nomadclub.cashchat.shared.hud.HudStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EvolutionViewModel(
    val evolutionStore: EvolutionStore,
    private val hudStore: HudStore,
) : ViewModel() {

    /** 연출 단계: IDLE → CHARGING(0.8s) → SURGING(1.2s) → REVEAL_SUCCESS/REVEAL_FAIL */
    enum class Phase { IDLE, CHARGING, SURGING, REVEAL_SUCCESS, REVEAL_FAIL }

    private val _phase = MutableStateFlow(Phase.IDLE)
    val phase = _phase.asStateFlow()

    private val _lastResult = MutableStateFlow<EvolutionAttemptDto?>(null)
    val lastResult = _lastResult.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    /** 2회차부터 연출 스킵 허용 */
    var attemptCount = 0
        private set
    private var skipRequested = false

    init {
        viewModelScope.launch {
            runCatching { evolutionStore.refresh() }
            if (FeatureFlags.EVOLUTION_HISTORY) runCatching { evolutionStore.refreshHistory() }
        }
    }

    fun requestSkip() { skipRequested = true }

    fun attempt() {
        if (_phase.value != Phase.IDLE) return
        attemptCount++
        skipRequested = false
        viewModelScope.launch {
            _phase.value = Phase.CHARGING
            val result = try {
                evolutionStore.attempt()
            } catch (e: ApiException) {
                _phase.value = Phase.IDLE
                _errorMessage.value = when (e.code) {
                    ApiException.INSUFFICIENT_POINTS -> "포인트가 부족해요. 광고로 모아볼까요?"
                    ApiException.ALREADY_MAX_LEVEL -> "이미 최고 레벨이에요!"
                    else -> e.message
                }
                runCatching { evolutionStore.refresh() }
                return@launch
            } catch (e: Exception) {
                _phase.value = Phase.IDLE
                _errorMessage.value = "네트워크 오류 — 다시 시도해주세요"
                return@launch
            }
            _lastResult.value = result

            // 연출 타임라인 (스킵 시 즉시 결과)
            if (!skipRequested) delay(800)            // CHARGING
            if (!skipRequested) { _phase.value = Phase.SURGING; delay(1200) }
            _phase.value = if (result.success) Phase.REVEAL_SUCCESS else Phase.REVEAL_FAIL

            // 성공 시 레벨·밥 보너스 반영 (스펙 §3.3)
            runCatching { evolutionStore.refresh() }
            hudStore.refresh()
        }
    }

    fun dismissResult() { _phase.value = Phase.IDLE; _lastResult.value = null }
    fun clearError() { _errorMessage.value = null }
}
