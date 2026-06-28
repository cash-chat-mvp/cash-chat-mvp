package com.nomadclub.cashchat.shared.localllm

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

class FakeLocalLlmEngine(
    initialState: EngineState = EngineState.UNINITIALIZED,
) : LocalLlmEngine {
    private val _state = MutableStateFlow(initialState)

    var loadError: Throwable? = null
    var generator: (String) -> Flow<String> = { emptyFlow() }

    val loadCalls = mutableListOf<String>()
    val prompts = mutableListOf<String>()
    var resetCalls = 0
        private set
    var releaseCalls = 0
        private set

    override val state: StateFlow<EngineState> = _state

    override suspend fun load(modelPath: String, params: SamplingParameters) {
        _state.value = EngineState.LOADING
        loadCalls += modelPath
        val error = loadError
        if (error != null) {
            _state.value = EngineState.ERROR
            throw error
        }
        _state.value = EngineState.READY
    }

    override fun generate(prompt: String): Flow<String> {
        return generator(prompt)
            .onStart {
                prompts += prompt
                _state.value = EngineState.GENERATING
            }
            .onCompletion { cause ->
                _state.value = when (cause) {
                    null, is CancellationException -> EngineState.READY
                    else -> EngineState.ERROR
                }
            }
    }

    override fun resetSession() {
        resetCalls += 1
        _state.value = EngineState.READY
    }

    override fun release() {
        releaseCalls += 1
        _state.value = EngineState.UNINITIALIZED
    }
}
