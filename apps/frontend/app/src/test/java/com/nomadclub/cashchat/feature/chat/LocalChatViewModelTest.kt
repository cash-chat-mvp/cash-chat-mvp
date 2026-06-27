package com.nomadclub.cashchat.feature.chat

import com.nomadclub.cashchat.shared.chat.model.ChatItem
import com.nomadclub.cashchat.shared.localllm.CapabilityResult
import com.nomadclub.cashchat.shared.localllm.ChatModeStore
import com.nomadclub.cashchat.shared.localllm.ChatModelMode
import com.nomadclub.cashchat.shared.localllm.EngineState
import com.nomadclub.cashchat.shared.localllm.GemmaModelSpec
import com.nomadclub.cashchat.shared.localllm.LocalChatHistory
import com.nomadclub.cashchat.shared.localllm.LocalChatStore
import com.nomadclub.cashchat.shared.localllm.LocalLlmEngine
import com.nomadclub.cashchat.shared.localllm.ModelDownloadState
import com.nomadclub.cashchat.shared.localllm.ModelDownloadStore
import com.nomadclub.cashchat.shared.localllm.ModelDownloader
import com.nomadclub.cashchat.shared.localllm.SamplingParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertFalse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalChatViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun selectGemmaSwitchesModeWhenDeviceCanRunGemma() {
        val modeStore = ChatModeStore()
        val viewModel = newViewModel(
            modeStore = modeStore,
            capability = CapabilityResult.Ok,
        )

        viewModel.selectGemma()

        assertEquals(ChatModelMode.GEMMA_LOCAL, modeStore.mode.value)
        assertTrue(viewModel.canSelectGemma.value)
    }

    @Test
    fun selectGemmaKeepsCashAiWhenDeviceCannotRunGemma() {
        val modeStore = ChatModeStore()
        val viewModel = newViewModel(
            modeStore = modeStore,
            capability = CapabilityResult.Insufficient("Storage is below 100 bytes."),
        )

        viewModel.selectGemma()

        assertEquals(ChatModelMode.CASH_AI, modeStore.mode.value)
        assertEquals("Storage is below 100 bytes.", viewModel.gemmaUnavailableReason.value)
    }

    @Test
    fun startModelDownloadDelegatesToDownloadStore() {
        val downloader = RecordingDownloader()
        val viewModel = newViewModel(downloader = downloader)

        viewModel.startModelDownload()
        scope.advanceUntilIdle()

        assertEquals(1, downloader.downloadCount)
        assertTrue(viewModel.downloadState.value is ModelDownloadState.Ready)
    }

    @Test
    fun downloadedModelDoesNotEnableGemmaChatWhenEngineIsUnavailable() {
        val viewModel = newViewModel(
            engineAvailability = GemmaEngineAvailability.Unavailable("Gemma engine is not linked."),
        )

        viewModel.startModelDownload()
        scope.advanceUntilIdle()

        assertTrue(viewModel.downloadState.value is ModelDownloadState.Ready)
        assertFalse(viewModel.canSendGemma.value)
        assertEquals("Gemma engine is not linked.", viewModel.engineUnavailableReason.value)
    }

    @Test
    fun selectingCashAiStopsLocalStream() {
        val modeStore = ChatModeStore(ChatModelMode.GEMMA_LOCAL)
        val directory = "/tmp/cash-chat-local-chat-viewmodel-stop-test"
        val spec = testSpec()
        java.io.File(directory).mkdirs()
        java.io.File(directory, spec.fileName).writeText("model")
        val viewModel = newViewModel(
            modeStore = modeStore,
            spec = spec,
            modelDirectory = directory,
            engine = NeverEndingLocalLlmEngine(),
        )

        viewModel.send("hello")
        scope.advanceUntilIdle()
        assertTrue(viewModel.isStreaming.value)

        viewModel.selectCashAi()
        scope.advanceUntilIdle()

        assertEquals(ChatModelMode.CASH_AI, modeStore.mode.value)
        assertFalse(viewModel.isStreaming.value)
    }

    private fun newViewModel(
        modeStore: ChatModeStore = ChatModeStore(),
        capability: CapabilityResult = CapabilityResult.Ok,
        downloader: RecordingDownloader = RecordingDownloader(),
        engineAvailability: GemmaEngineAvailability = GemmaEngineAvailability.Available,
        spec: GemmaModelSpec = testSpec(),
        modelDirectory: String = "/tmp/cash-chat-local-chat-viewmodel-test",
        engine: LocalLlmEngine = NoopLocalLlmEngine(),
    ): LocalChatViewModel {
        val downloadStore = ModelDownloadStore(
            spec = spec,
            downloader = downloader,
            scope = scope,
            modelDirectory = modelDirectory,
        )
        return LocalChatViewModel(
            modeStore = modeStore,
            downloadStore = downloadStore,
            localChatStore = LocalChatStore(
                engine = engine,
                history = InMemoryLocalChatHistory(),
                scope = scope,
                modelSpec = spec,
                modelDirectory = modelDirectory,
            ),
            gemmaSpec = spec,
            engineAvailability = engineAvailability,
            capabilityProvider = { capability },
        )
    }

    private fun testSpec() = GemmaModelSpec(
        variantId = "test-gemma",
        fileName = "test-gemma.litertlm",
        url = "https://example.invalid/model",
        sha256 = "abc",
        sizeBytes = 1,
        minRamBytes = 1,
    )
}

private class RecordingDownloader : ModelDownloader {
    var downloadCount = 0

    override fun download(
        spec: GemmaModelSpec,
        destinationPath: String,
    ): Flow<ModelDownloadState> {
        downloadCount += 1
        return flowOf(ModelDownloadState.Ready(destinationPath))
    }

    override fun cancel() = Unit
}

private class NoopLocalLlmEngine : LocalLlmEngine {
    override val state = MutableStateFlow(EngineState.UNINITIALIZED)

    override suspend fun load(modelPath: String, params: SamplingParameters) {
        state.value = EngineState.READY
    }

    override fun generate(prompt: String): Flow<String> = flowOf("ok")

    override fun resetSession() = Unit

    override fun release() = Unit
}

private class NeverEndingLocalLlmEngine : LocalLlmEngine {
    override val state = MutableStateFlow(EngineState.UNINITIALIZED)

    override suspend fun load(modelPath: String, params: SamplingParameters) {
        state.value = EngineState.READY
    }

    override fun generate(prompt: String): Flow<String> = flow {
        state.value = EngineState.GENERATING
        awaitCancellation()
    }

    override fun resetSession() = Unit

    override fun release() = Unit
}

private class InMemoryLocalChatHistory : LocalChatHistory {
    override suspend fun load(): List<ChatItem> = emptyList()

    override suspend fun save(items: List<ChatItem>) = Unit

    override suspend fun clear() = Unit
}
