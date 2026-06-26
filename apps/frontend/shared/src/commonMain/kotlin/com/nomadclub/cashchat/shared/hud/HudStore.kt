package com.nomadclub.cashchat.shared.hud

import com.nomadclub.cashchat.shared.core.config.FeatureFlags
import com.nomadclub.cashchat.shared.energy.EnergyApi
import com.nomadclub.cashchat.shared.evolution.EvolutionApi
import com.nomadclub.cashchat.shared.wallet.PointsApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 채팅 톱바(HUD) 상태 (스펙 §2.4).
 * points는 BE에 잔액 조회 API가 없어 현재 null 고정 — UI는 null이면 코인 칩을 숨긴다.
 * TODO(BE): 포인트 잔액 API 추가되면 연결.
 */
data class HudState(
    val level: Int = 1,
    val isMaxLevel: Boolean = false,
    val energy: Int = 0,
    val maxEnergy: Int = 0,
    val points: Long? = null,
    /** 진화 경험치(R2 비용 통화). BE currentExp 미배포 시 null. */
    val exp: Long? = null,
    /** P1-3 — ISO-8601 원본. 파싱은 플랫폼단(java.time 등)에서. */
    val nextRecoverAt: String? = null,
    val isLoaded: Boolean = false,
)

class HudStore(
    private val energyApi: EnergyApi,
    private val evolutionApi: EvolutionApi,
    private val pointsApi: PointsApi,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(HudState())
    val state: StateFlow<HudState> = _state.asStateFlow()

    fun refresh() {
        scope.launch { runCatching { refreshNow() } }
    }

    @Throws(Exception::class)
    suspend fun refreshNow() = coroutineScope {
        val energyDeferred = async { energyApi.getMyEnergy() }
        val evolutionDeferred = async { evolutionApi.getState() }
        val pointsDeferred =
            if (FeatureFlags.POINT_BALANCE) async { runCatching { pointsApi.getBalance().balance }.getOrNull() } else null
        val energy = energyDeferred.await()
        val evolution = evolutionDeferred.await()
        _state.value = HudState(
            level = evolution.level,
            isMaxLevel = evolution.isMaxLevel,
            energy = energy.energy,
            maxEnergy = energy.maxEnergy,
            points = pointsDeferred?.await(),
            exp = evolution.currentExp,
            nextRecoverAt = energy.nextRecoverAt,
            isLoaded = true,
        )
    }

    @Throws(Exception::class)
    suspend fun refreshEnergyOnly() {
        val energy = energyApi.getMyEnergy()
        _state.value = _state.value.copy(
            energy = energy.energy,
            maxEnergy = energy.maxEnergy,
            nextRecoverAt = energy.nextRecoverAt,
            isLoaded = true,
        )
    }

    /** 로그아웃/세션 종료 시 다음 사용자에게 이전 레벨·에너지·포인트가 노출되지 않도록 초기화한다. */
    fun reset() {
        _state.value = HudState()
    }
}
