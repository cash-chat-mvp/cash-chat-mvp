package com.nomadclub.cashchat.shared.localllm

import kotlinx.coroutines.flow.Flow

data class DownloadProgress(
    val receivedBytes: Long,
    val totalBytes: Long,
) {
    val fraction: Double
        get() = if (totalBytes <= 0L) 0.0 else receivedBytes.toDouble() / totalBytes.toDouble()
}

sealed interface ModelDownloadState {
    data object NotDownloaded : ModelDownloadState

    data class Downloading(
        val receivedBytes: Long,
        val totalBytes: Long,
    ) : ModelDownloadState {
        val progress: DownloadProgress = DownloadProgress(receivedBytes, totalBytes)
    }

    data object Verifying : ModelDownloadState

    data class Ready(val localPath: String) : ModelDownloadState

    data class Failed(val reason: String) : ModelDownloadState
}

interface ModelDownloader {
    fun download(
        spec: GemmaModelSpec,
        destinationPath: String = modelFilePath(spec),
    ): Flow<ModelDownloadState>

    fun cancel()
}
