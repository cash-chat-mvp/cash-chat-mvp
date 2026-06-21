package com.nomadclub.cashchat.shared.roulette

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 룰렛 상태 보유 + 스핀 오케스트레이션. 채팅·리워드 경로와 무관하게 독립 동작.
 * 스핀 정책: 하루 첫 1회 무료(spin), 이후 매 스핀 광고 게이트(prepareAdSpin → 광고 → spinWithAd).
 * @param onEnergyChanged 스핀 후 에너지가 바뀌었을 수 있어 HUD 등을 갱신하는 콜백(DI 에서 HudStore.refreshEnergyOnly 주입).
 * iOS 에서 호출하므로 suspend 는 @Throws.
 */
class RouletteStore(
    private val repo: RouletteRepository,
    private val onEnergyChanged: suspend () -> Unit,
) {
    private val _status = MutableStateFlow<RouletteStatus?>(null)
    val status: StateFlow<RouletteStatus?> = _status.asStateFlow()

    @Throws(Exception::class)
    suspend fun refresh(): RouletteStatus = repo.getStatus().also { _status.value = it }

    /** 무료 첫 스핀 → 결과 반환. 서버(스텁)가 에너지 지급하므로 onEnergyChanged 로 HUD 동기화. */
    @Throws(Exception::class)
    suspend fun spin(): RouletteSpinResult = finishSpin(repo.spin())

    /** 광고 게이트 스핀용 nonce 발급(iOS 가 광고 표시 전에 호출). */
    @Throws(Exception::class)
    suspend fun prepareAdSpin(): String = repo.prepareAdSpin()

    /** 광고 시청 후 스핀 → 결과 반환. */
    @Throws(Exception::class)
    suspend fun spinWithAd(): RouletteSpinResult = finishSpin(repo.spinWithAd())

    private suspend fun finishSpin(result: RouletteSpinResult): RouletteSpinResult {
        onEnergyChanged()
        _status.value = repo.getStatus()
        return result
    }
}
