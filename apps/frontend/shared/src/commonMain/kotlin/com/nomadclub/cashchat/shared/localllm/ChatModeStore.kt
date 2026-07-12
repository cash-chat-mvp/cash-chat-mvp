package com.nomadclub.cashchat.shared.localllm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ChatModelMode {
    CASH_AI,
    GEMMA_LOCAL,
}

class ChatModeStore(
    initialMode: ChatModelMode = ChatModelMode.CASH_AI,
) {
    private val _mode = MutableStateFlow(initialMode)

    val mode: StateFlow<ChatModelMode> = _mode.asStateFlow()

    fun select(mode: ChatModelMode) {
        _mode.value = mode
    }

    fun reset() {
        _mode.value = ChatModelMode.CASH_AI
    }
}
