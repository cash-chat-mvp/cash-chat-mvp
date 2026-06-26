package com.nomadclub.cashchat.shared.localllm

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val ModelDownloadStoreJson = Json {
    ignoreUnknownKeys = true
}

@Serializable
private data class StoredModelRecord(
    val variantId: String,
    val sha256: String,
    val localPath: String,
    val sizeBytes: Long,
)

class ModelDownloadStore(
    val spec: GemmaModelSpec,
    private val downloader: ModelDownloader,
    private val scope: CoroutineScope,
    private val modelDirectory: String = localModelsDir(),
) {
    private val _state = MutableStateFlow<ModelDownloadState>(ModelDownloadState.NotDownloaded)
    private var downloadJob: Job? = null

    val state: StateFlow<ModelDownloadState> = _state.asStateFlow()

    val localPath: String
        get() = modelFilePath(spec, modelDirectory)

    fun refresh() {
        ensureDirectory(modelDirectory)
        val record = readRecord()
        if (record == null) {
            _state.value = ModelDownloadState.NotDownloaded
            return
        }

        if (
            record.variantId == spec.variantId &&
            record.sha256 == spec.sha256 &&
            record.localPath == localPath &&
            record.sizeBytes == spec.sizeBytes &&
            fileExists(record.localPath)
        ) {
            _state.value = ModelDownloadState.Ready(record.localPath)
        } else {
            deleteFile(metadataPath())
            _state.value = ModelDownloadState.NotDownloaded
        }
    }

    fun start() {
        if (downloadJob?.isActive == true) return
        ensureDirectory(modelDirectory)

        downloadJob = scope.launch {
            try {
                downloader.download(spec, localPath).collect { next ->
                    _state.value = next
                    when (next) {
                        is ModelDownloadState.Ready -> writeRecord(
                            StoredModelRecord(
                                variantId = spec.variantId,
                                sha256 = spec.sha256,
                                localPath = next.localPath,
                                sizeBytes = spec.sizeBytes,
                            ),
                        )

                        is ModelDownloadState.Failed -> deleteFile(metadataPath())
                        else -> Unit
                    }
                }
            } catch (_: CancellationException) {
                _state.value = ModelDownloadState.NotDownloaded
            } catch (t: Throwable) {
                deleteFile(metadataPath())
                _state.value = ModelDownloadState.Failed(t.message ?: "Download failed.")
            } finally {
                downloadJob = null
            }
        }
    }

    fun cancel() {
        downloadJob?.cancel()
        downloader.cancel()
        if (_state.value !is ModelDownloadState.Ready) {
            _state.value = ModelDownloadState.NotDownloaded
        }
    }

    private fun metadataPath(): String = joinPath(modelDirectory, "${spec.fileName}.metadata.json")

    private fun readRecord(): StoredModelRecord? {
        val content = readTextFile(metadataPath()) ?: return null
        return runCatching {
            ModelDownloadStoreJson.decodeFromString<StoredModelRecord>(content)
        }.getOrNull()
    }

    private fun writeRecord(record: StoredModelRecord) {
        parentPath(metadataPath())?.let(::ensureDirectory)
        writeTextFile(metadataPath(), ModelDownloadStoreJson.encodeToString(record))
    }
}
