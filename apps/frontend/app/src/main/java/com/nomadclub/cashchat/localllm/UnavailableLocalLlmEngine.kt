package com.nomadclub.cashchat.localllm

import com.nomadclub.cashchat.shared.localllm.EngineState
import com.nomadclub.cashchat.shared.localllm.LocalLlmEngine
import com.nomadclub.cashchat.shared.localllm.SamplingParameters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

class UnavailableLocalLlmEngine : LocalLlmEngine {
    override val state = MutableStateFlow(EngineState.UNINITIALIZED)

    override suspend fun load(modelPath: String, params: SamplingParameters) {
        state.value = EngineState.ERROR
        error("Gemma on-device engine is not linked in this build.")
    }

    override fun generate(prompt: String): Flow<String> = flow {
        state.value = EngineState.ERROR
        error("Gemma on-device engine is not linked in this build.")
    }

    override fun resetSession() {
        state.value = EngineState.UNINITIALIZED
    }

    override fun release() {
        state.value = EngineState.UNINITIALIZED
    }
}
