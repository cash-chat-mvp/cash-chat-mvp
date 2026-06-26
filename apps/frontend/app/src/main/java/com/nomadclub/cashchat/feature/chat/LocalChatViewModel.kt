package com.nomadclub.cashchat.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nomadclub.cashchat.shared.localllm.CapabilityResult
import com.nomadclub.cashchat.shared.localllm.ChatModeStore
import com.nomadclub.cashchat.shared.localllm.ChatModelMode
import com.nomadclub.cashchat.shared.localllm.GemmaModelSpec
import com.nomadclub.cashchat.shared.localllm.LocalChatStore
import com.nomadclub.cashchat.shared.localllm.ModelDownloadStore
import com.nomadclub.cashchat.shared.localllm.ModelDownloadState
import com.nomadclub.cashchat.shared.localllm.canRunGemma
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface GemmaEngineAvailability {
    data object Available : GemmaEngineAvailability

    data class Unavailable(val reason: String) : GemmaEngineAvailability
}

class LocalChatViewModel(
    private val modeStore: ChatModeStore,
    private val downloadStore: ModelDownloadStore,
    val localChatStore: LocalChatStore,
    val gemmaSpec: GemmaModelSpec,
    engineAvailability: GemmaEngineAvailability = GemmaEngineAvailability.Available,
    private val capabilityProvider: (GemmaModelSpec) -> CapabilityResult = { canRunGemma(it) },
) : ViewModel() {
    val mode: StateFlow<ChatModelMode> = modeStore.mode
    val downloadState = downloadStore.state
    val items = localChatStore.items
    val isStreaming = localChatStore.isStreaming
    val engineState = localChatStore.engineState

    private val capability = capabilityProvider(gemmaSpec)
    private val _gemmaUnavailableReason = MutableStateFlow(
        (capability as? CapabilityResult.Insufficient)?.reason,
    )
    val gemmaUnavailableReason: StateFlow<String?> = _gemmaUnavailableReason.asStateFlow()

    private val _canSelectGemma = MutableStateFlow(capability is CapabilityResult.Ok)
    val canSelectGemma: StateFlow<Boolean> = _canSelectGemma.asStateFlow()
    private val engineAvailable = engineAvailability is GemmaEngineAvailability.Available
    private val _engineUnavailableReason = MutableStateFlow(
        (engineAvailability as? GemmaEngineAvailability.Unavailable)?.reason,
    )
    val engineUnavailableReason: StateFlow<String?> = _engineUnavailableReason.asStateFlow()
    val canSendGemma: StateFlow<Boolean> = downloadStore.state
        .map { state -> state is ModelDownloadState.Ready && engineAvailable }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        downloadStore.refresh()
    }

    fun selectCashAi() {
        localChatStore.stop()
        modeStore.select(ChatModelMode.CASH_AI)
    }

    fun selectGemma() {
        if (capability is CapabilityResult.Ok) {
            _gemmaUnavailableReason.value = null
            modeStore.select(ChatModelMode.GEMMA_LOCAL)
        } else {
            _gemmaUnavailableReason.value = (capability as CapabilityResult.Insufficient).reason
        }
    }

    fun startModelDownload() {
        downloadStore.start()
    }

    fun cancelModelDownload() {
        downloadStore.cancel()
    }

    fun send(text: String) {
        localChatStore.sendMessage(text)
    }

    fun stop() {
        localChatStore.stop()
    }

    fun clear() {
        localChatStore.clear()
    }

    override fun onCleared() {
        localChatStore.releaseEngine()
        viewModelScope.coroutineContext.cancelChildren()
        super.onCleared()
    }
}
