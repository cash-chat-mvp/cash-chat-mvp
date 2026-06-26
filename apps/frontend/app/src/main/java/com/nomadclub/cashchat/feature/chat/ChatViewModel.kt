package com.nomadclub.cashchat.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nomadclub.cashchat.shared.ads.AdRewardStore
import com.nomadclub.cashchat.shared.chat.ChatResourceFeedback
import com.nomadclub.cashchat.shared.chat.ChatStore
import com.nomadclub.cashchat.shared.hud.HudStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(
    val chatStore: ChatStore,
    val hudStore: HudStore,
    val adRewardStore: AdRewardStore,
) : ViewModel() {

    /** 게이트 시트의 광고 보상 단계 표시용 */
    enum class RewardPhase { IDLE, SHOWING_AD, POLLING, FAILED }

    private val _rewardPhase = MutableStateFlow(RewardPhase.IDLE)
    val rewardPhase = _rewardPhase.asStateFlow()

    val resourceFeedback = chatStore.resourceFeedback
    val latestResourceFeedback = resourceFeedback
        .map<ChatResourceFeedback, ChatResourceFeedback?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** 완료 보상(🪙/⭐) 토큰 연출용 — 메시지 ID와 변화량을 담은 마지막 RewardEarned. */
    val rewardEarned = resourceFeedback
        .filterIsInstance<ChatResourceFeedback.RewardEarned>()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        hudStore.refresh()
        viewModelScope.launch {
            // 완료 즉시 HUD 잔액을 재조회(연출과 병렬). 토큰 연출은 rewardEarned 가 독립 구동한다.
            chatStore.streamCompletedCount.collect {
                if (it > 0) runCatching { hudStore.refreshNow() }
            }
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
            // 광고 표시 직전의 보상 적립 횟수를 baseline 으로 잡고, 광고 후 usedToday 증가로만 적립을 판정한다.
            val watched = runCatching {
                val baselineUsed = adRewardStore.refreshQuota().usedToday
                lastRewardBaseline = baselineUsed
                val nonce = adRewardStore.requestNonce()
                showAd(nonce)
            }.getOrDefault(false)

            // 광고를 끝까지 보지 않았거나 준비 실패 → FAILED(보상 확인 지연)가 아니라 초기 상태로 복귀.
            if (!watched) {
                _rewardPhase.value = RewardPhase.IDLE
                return@launch
            }

            _rewardPhase.value = RewardPhase.POLLING
            val applied = runCatching {
                adRewardStore.awaitRewardApplied(lastRewardBaseline ?: 0)
            }.getOrDefault(false)
            finishRewardPolling(applied)
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
