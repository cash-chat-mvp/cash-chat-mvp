package com.nomadclub.cashchat.shared.roulette

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 룰렛 상태 보유 + 스핀/광고크레딧 오케스트레이션. 채팅·리워드 경로와 무관하게 독립 동작.
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

    /** 보유 스핀 1개 소모 → 결과 반환. 서버(스텁)가 에너지 지급하므로 onEnergyChanged 로 HUD 동기화. */
    @Throws(Exception::class)
    suspend fun spin(): RouletteSpinResult {
        val result = repo.spin()
        onEnergyChanged()
        _status.value = repo.getStatus()
        return result
    }

    /**
     * 광고 시청 → 스핀 크레딧 적립. showAd 는 nonce 로 광고를 띄우고 끝까지 봤으면 true.
     * (Android 용. iOS 는 suspend-lambda 파라미터를 Swift 클로저로 못 넘기므로 prepareAdSpin/creditAdSpin 을 직접 호출한다.)
     */
    @Throws(Exception::class)
    suspend fun watchAdForSpin(showAd: suspend (nonce: String) -> Boolean): Boolean {
        val baseline = repo.getStatus().availableSpins
        val nonce = prepareAdSpin()
        val watched = showAd(nonce)
        if (!watched) return false
        return creditAdSpin(baseline)
    }

    /** 광고 추가 스핀용 nonce 발급(iOS 가 광고 표시 전에 호출). */
    @Throws(Exception::class)
    suspend fun prepareAdSpin(): String = repo.requestAdSpinNonce()

    /** 광고 시청 후 스핀 크레딧 적립 판정 + status 갱신. baseline 대비 availableSpins 증가 시 true. */
    @Throws(Exception::class)
    suspend fun creditAdSpin(baselineAvailable: Int): Boolean {
        val credited = repo.awaitSpinCredited(baselineAvailable)
        _status.value = repo.getStatus()
        return credited
    }
}
