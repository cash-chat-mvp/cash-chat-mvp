package com.nomadclub.cashchat.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nomadclub.cashchat.shared.ads.AdRewardStore
import com.nomadclub.cashchat.shared.chat.ChatStore
import com.nomadclub.cashchat.shared.hud.HudStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    init {
        hudStore.refresh()
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

    /** 게이트 CTA: nonce 발급 → 광고 표시 콜백 → 폴링 → 재전송 */
    fun startAdReward(showAd: suspend (nonce: String) -> Boolean) {
        viewModelScope.launch {
            _rewardPhase.value = RewardPhase.SHOWING_AD
            val baseline = hudStore.state.value.energy
            val result = runCatching {
                val nonce = adRewardStore.requestNonce()
                if (!showAd(nonce)) return@runCatching false
                _rewardPhase.value = RewardPhase.POLLING
                adRewardStore.awaitRewardApplied(baseline)
            }.getOrDefault(false)

            runCatching { hudStore.refreshEnergyOnly() }
            runCatching { adRewardStore.refreshQuota() }
            if (result) {
                _rewardPhase.value = RewardPhase.IDLE
                chatStore.retryBlocked()
            } else {
                _rewardPhase.value = RewardPhase.FAILED
            }
        }
    }

    fun dismissGate() {
        _rewardPhase.value = RewardPhase.IDLE
        chatStore.dismissEnergyGate()
    }
}
