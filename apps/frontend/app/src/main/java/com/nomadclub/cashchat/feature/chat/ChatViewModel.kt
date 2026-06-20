package com.nomadclub.cashchat.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nomadclub.cashchat.shared.ads.AdRewardStore
import com.nomadclub.cashchat.shared.attendance.AttendanceApi
import com.nomadclub.cashchat.shared.attendance.CheckInDto
import com.nomadclub.cashchat.shared.attendance.MonthlyAttendanceDto
import com.nomadclub.cashchat.shared.chat.ChatStore
import com.nomadclub.cashchat.shared.hud.HudStore
import com.nomadclub.cashchat.shared.points.PointsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(
    val chatStore: ChatStore,
    val hudStore: HudStore,
    val adRewardStore: AdRewardStore,
    private val attendanceApi: AttendanceApi,
    private val pointsRepository: PointsRepository,
) : ViewModel() {

    /** 게이트 시트의 광고 보상 단계 표시용 */
    enum class RewardPhase { IDLE, SHOWING_AD, POLLING, FAILED }

    private val _rewardPhase = MutableStateFlow(RewardPhase.IDLE)
    val rewardPhase = _rewardPhase.asStateFlow()

    private val _attendance = MutableStateFlow<MonthlyAttendanceDto?>(null)
    val attendance = _attendance.asStateFlow()

    private val _checkInResult = MutableStateFlow<CheckInDto?>(null)
    val checkInResult = _checkInResult.asStateFlow()

    init {
        hudStore.refresh()
        viewModelScope.launch {
            // 채팅 진입 시 자동 출석 — 이미 출석한 날은 조용히 통과 (409 ALREADY_CHECKED_IN 포함)
            runCatching {
                val monthly = attendanceApi.getMonthly()
                _attendance.value = monthly
                if (!monthly.todayChecked) {
                    val result = attendanceApi.checkIn()
                    _checkInResult.value = result
                    // 혜택존/상점이 쓰는 공유 잔액에 적립 코인을 반영 (AttendanceStore.checkIn 과 동일 처리).
                    // 이 경로는 store 를 거치지 않으므로 직접 반영하지 않으면 다른 화면 잔액이 갱신되지 않는다.
                    pointsRepository.applyDelta(result.awardedCoin)
                    _attendance.value = attendanceApi.getMonthly()
                }
            }
        }
        viewModelScope.launch {
            chatStore.streamCompletedCount.collect { if (it > 0) runCatching { hudStore.refreshEnergyOnly() } }
        }
        viewModelScope.launch {
            chatStore.energyGateVisible.collect { visible ->
                if (visible) runCatching { adRewardStore.refreshQuota() }
            }
        }
    }

    fun send(text: String) = chatStore.sendMessage(text)

    fun openConversation(id: Long) {
        viewModelScope.launch { runCatching { chatStore.openConversation(id) } }
    }

    // 광고 표시 직전의 보상 적립 횟수(usedToday). FAILED 후 폴링만 재시도할 때 동일 baseline 으로 판정한다.
    private var lastRewardBaseline: Int? = null

    /** 게이트 CTA: nonce 발급 → 광고 표시 콜백 → 폴링 → 재전송 */
    fun startAdReward(showAd: suspend (nonce: String) -> Boolean) {
        viewModelScope.launch {
            _rewardPhase.value = RewardPhase.SHOWING_AD
            val result = runCatching {
                // 광고 표시 직전의 보상 적립 횟수를 baseline 으로 잡고, 광고 후 usedToday 증가로만 적립을 판정한다.
                val baselineUsed = adRewardStore.refreshQuota().usedToday
                lastRewardBaseline = baselineUsed
                val nonce = adRewardStore.requestNonce()
                if (!showAd(nonce)) return@runCatching false
                _rewardPhase.value = RewardPhase.POLLING
                adRewardStore.awaitRewardApplied(baselineUsed)
            }.getOrDefault(false)

            finishRewardPolling(result)
        }
    }

    /**
     * 보상 확인 지연(FAILED) 후 "다시 확인": 광고 재시청 없이 원래 baseline 으로 폴링만 재시도한다.
     * 새 baseline/nonce 를 발급하면 이미 적립된 보상이 baseline 에 포함돼 영영 true 가 안 되는 문제를 막는다.
     */
    fun retryRewardPolling() {
        val baseline = lastRewardBaseline
        if (baseline == null) {
            // baseline 정보가 없으면 정상 플로우로 폴백 (드문 케이스)
            _rewardPhase.value = RewardPhase.FAILED
            return
        }
        viewModelScope.launch {
            _rewardPhase.value = RewardPhase.POLLING
            val result = runCatching { adRewardStore.awaitRewardApplied(baseline) }.getOrDefault(false)
            finishRewardPolling(result)
        }
    }

    private suspend fun finishRewardPolling(applied: Boolean) {
        runCatching { hudStore.refreshEnergyOnly() }
        runCatching { adRewardStore.refreshQuota() }
        if (applied) {
            lastRewardBaseline = null
            _rewardPhase.value = RewardPhase.IDLE
            chatStore.retryBlocked()
        } else {
            _rewardPhase.value = RewardPhase.FAILED
        }
    }

    fun dismissGate() {
        _rewardPhase.value = RewardPhase.IDLE
        chatStore.dismissEnergyGate()
    }

    fun dismissCheckIn() { _checkInResult.value = null }

    /** 회복 카운트다운 종료 등 — 에너지만 재조회 */
    fun refreshEnergy() {
        viewModelScope.launch { runCatching { hudStore.refreshEnergyOnly() } }
    }

    /** Ad Gate 해제 (P2-3): nonce 발급 → 광고 표시 → 성공 시 해당 메시지 blur 해제 */
    fun startGateUnlock(messageId: String, showAd: suspend (nonce: String) -> Boolean) {
        viewModelScope.launch {
            val watched = runCatching {
                val nonce = adRewardStore.requestNonce()
                showAd(nonce)
            }.getOrDefault(false)
            if (watched) chatStore.unlockGatedMessage(messageId)
        }
    }
}
