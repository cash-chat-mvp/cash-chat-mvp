package com.nomadclub.cashchat.shared.localllm

import com.nomadclub.cashchat.shared.chat.model.ChatItem
import com.nomadclub.cashchat.shared.platform.currentTimeMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LocalChatStore(
    private val engine: LocalLlmEngine,
    private val history: LocalChatHistory,
    private val scope: CoroutineScope,
    private val modelSpec: GemmaModelSpec = DEFAULT_GEMMA_SPEC,
    private val modelDirectory: String = localModelsDir(),
    private val samplingParameters: SamplingParameters = SamplingParameters(),
) {
    private val _items = MutableStateFlow<List<ChatItem>>(emptyList())
    private val _isStreaming = MutableStateFlow(false)
    private var streamJob: Job? = null
    private var engineLoaded = false
    private var idCounter = 0L

    val items: StateFlow<List<ChatItem>> = _items.asStateFlow()
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()
    val engineState: StateFlow<EngineState> = engine.state

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            _items.value = history.load()
        }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _isStreaming.value) return
        _isStreaming.value = true

        val userId = nextId("u")
        val assistantId = nextId("a")
        _items.update {
            it + ChatItem.UserMessage(
                id = userId,
                text = trimmed,
                status = ChatItem.SendStatus.CONFIRMED,
            ) + ChatItem.AssistantMessage(
                id = assistantId,
                text = "",
                isStreaming = true,
            )
        }

        streamJob = scope.launch {
            try {
                ensureLoaded()
                saveHistorySnapshot()
                engine.generate(trimmed).collect { token ->
                    updateAssistant(assistantId) { current ->
                        current.copy(text = current.text + token)
                    }
                }
                updateAssistant(assistantId) { it.copy(isStreaming = false) }
            } catch (_: CancellationException) {
                updateAssistant(assistantId) { it.copy(isStreaming = false) }
            } catch (_: Throwable) {
                updateAssistant(assistantId) { it.copy(isStreaming = false, isError = true) }
            } finally {
                _isStreaming.value = false
                saveHistorySnapshot()
                streamJob = null
            }
        }
    }

    fun stop() {
        streamJob?.cancel()
    }

    fun clear() {
        streamJob?.cancel()
        _items.value = emptyList()
        engine.resetSession()
        saveHistoryAsync()
        scope.launch { history.clear() }
    }

    fun releaseEngine() {
        streamJob?.cancel()
        engine.release()
        engineLoaded = false
    }

    private suspend fun ensureLoaded() {
        if (engineLoaded && engine.state.value != EngineState.ERROR && engine.state.value != EngineState.UNINITIALIZED) {
            return
        }

        val path = modelFilePath(modelSpec, modelDirectory)
        if (!fileExists(path)) {
            throw IllegalStateException("Model file is missing at $path")
        }

        engine.load(path, samplingParameters)
        engineLoaded = true
    }

    private fun nextId(prefix: String): String {
        idCounter += 1
        return "$prefix${currentTimeMillis()}_$idCounter"
    }

    private suspend fun saveHistorySnapshot() {
        history.save(_items.value)
    }

    private fun saveHistoryAsync() {
        scope.launch {
            saveHistorySnapshot()
        }
    }

    private fun updateAssistant(
        id: String,
        transform: (ChatItem.AssistantMessage) -> ChatItem.AssistantMessage,
    ) {
        _items.update { items ->
            items.map { item ->
                if (item is ChatItem.AssistantMessage && item.id == id) transform(item) else item
            }
        }
    }
}
