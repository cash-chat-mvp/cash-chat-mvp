package com.nomadclub.cashchat.shared.localllm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class EngineState {
    UNINITIALIZED,
    LOADING,
    READY,
    GENERATING,
    ERROR,
}

data class SamplingParameters(
    val temperature: Float = 1.0f,
    val topP: Float = 0.95f,
    val topK: Int = 64,
    val maxTokens: Int = 2_048,
)

interface LocalLlmEngine {
    val state: StateFlow<EngineState>

    @Throws(Exception::class)
    suspend fun load(
        modelPath: String,
        params: SamplingParameters = SamplingParameters(),
    )

    fun generate(prompt: String): Flow<String>

    fun resetSession()

    fun release()
}
