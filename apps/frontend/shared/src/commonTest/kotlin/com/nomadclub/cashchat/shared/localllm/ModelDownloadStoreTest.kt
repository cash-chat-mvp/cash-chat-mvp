package com.nomadclub.cashchat.shared.localllm

import kotlin.random.Random
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private class FakeModelDownloader : ModelDownloader {
    var emissions: (String) -> Flow<ModelDownloadState> = { flow { } }
    var cancelCalls = 0
    var requestedPath: String? = null

    override fun download(spec: GemmaModelSpec, destinationPath: String): Flow<ModelDownloadState> {
        requestedPath = destinationPath
        return emissions(destinationPath)
    }

    override fun cancel() {
        cancelCalls += 1
    }
}

class ModelDownloadStoreTest {

    @Test
    fun `refresh 는 메타데이터가 없으면 NotDownloaded`() = runTest {
        val spec = testSpec("refresh")
        val store = ModelDownloadStore(spec, FakeModelDownloader(), this, testDirectory("refresh"))

        store.refresh()

        assertEquals(ModelDownloadState.NotDownloaded, store.state.value)
    }

    @Test
    fun `start 완료 후 새 store refresh 는 Ready 를 복원한다`() = runTest {
        val directory = testDirectory("ready")
        val spec = testSpec("ready")
        val downloader = FakeModelDownloader().apply {
            emissions = { path ->
                flow {
                    emit(ModelDownloadState.Downloading(receivedBytes = 512L, totalBytes = 1_024L))
                    emit(ModelDownloadState.Verifying)
                    writeFileBytes(path, byteArrayOf(1, 2, 3))
                    emit(ModelDownloadState.Ready(path))
                }
            }
        }
        val store = ModelDownloadStore(spec, downloader, this, directory)

        store.start()
        testScheduler.advanceUntilIdle()

        val restored = ModelDownloadStore(spec, FakeModelDownloader(), this, directory)
        restored.refresh()

        assertIs<ModelDownloadState.Ready>(store.state.value)
        assertIs<ModelDownloadState.Ready>(restored.state.value)
    }

    @Test
    fun `cancel 은 진행 중 다운로드를 멈추고 NotDownloaded 로 돌린다`() = runTest {
        val downloader = FakeModelDownloader().apply {
            emissions = {
                flow {
                    emit(ModelDownloadState.Downloading(receivedBytes = 128L, totalBytes = 1_024L))
                    awaitCancellation()
                }
            }
        }
        val store = ModelDownloadStore(testSpec("cancel"), downloader, this, testDirectory("cancel"))

        store.start()
        testScheduler.runCurrent()
        store.cancel()
        testScheduler.advanceUntilIdle()

        assertEquals(ModelDownloadState.NotDownloaded, store.state.value)
        assertEquals(1, downloader.cancelCalls)
    }

    private fun testSpec(name: String) = DEFAULT_GEMMA_SPEC.copy(
        fileName = "$name-${Random.nextInt().toUInt().toString(16)}.litertlm",
        sha256 = "UNRESOLVED_GEMMA_MODEL_SHA256",
        sizeBytes = 1_024L,
    )

    private fun testDirectory(name: String): String {
        val directory = "${localModelsDir().trimEnd('/')}/localllm-tests/$name-${Random.nextInt().toUInt().toString(16)}"
        ensureDirectory(directory)
        return directory
    }
}
