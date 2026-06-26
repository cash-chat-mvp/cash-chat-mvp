package com.nomadclub.cashchat.shared.localllm

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

private const val DOWNLOAD_BUFFER_SIZE = 64 * 1024

class KtorModelDownloader(
    private val client: HttpClient,
) : ModelDownloader {

    override fun download(
        spec: GemmaModelSpec,
        destinationPath: String,
    ): Flow<ModelDownloadState> = flow {
        val resolvedUrl = spec.url.takeUnless(::isUnresolvedModelValue)
        if (resolvedUrl == null) {
            emit(ModelDownloadState.Failed("Model URL is unresolved."))
            return@flow
        }

        val expectedHash = spec.sha256.takeUnless(::isUnresolvedModelValue)
        if (expectedHash == null) {
            emit(ModelDownloadState.Failed("Model SHA-256 is unresolved."))
            return@flow
        }

        val tempPath = "$destinationPath.part"
        parentPath(tempPath)?.let(::ensureDirectory)

        val partialBytes = fileSize(tempPath).coerceAtLeast(0L)
        val response = client.get(resolvedUrl) {
            if (partialBytes > 0L) {
                header(HttpHeaders.Range, "bytes=$partialBytes-")
            }
        }

        if (!response.status.isSuccess()) {
            emit(ModelDownloadState.Failed("Download failed with HTTP ${response.status.value}."))
            return@flow
        }

        val resumed = partialBytes > 0L && response.status == HttpStatusCode.PartialContent
        if (!resumed && partialBytes > 0L) {
            deleteFile(tempPath)
        }

        var receivedBytes = if (resumed) partialBytes else 0L
        val totalBytes = parseTotalBytes(response.headers[HttpHeaders.ContentRange], response.contentLength(), resumed, partialBytes)
            ?: spec.sizeBytes

        emit(ModelDownloadState.Downloading(receivedBytes = receivedBytes, totalBytes = totalBytes))

        val channel = response.bodyAsChannel()
        val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
        var append = resumed

        while (true) {
            val read = channel.readAvailable(buffer, 0, buffer.size)
            if (read <= 0) break

            writeFileBytes(
                path = tempPath,
                bytes = buffer.copyOf(read),
                append = append,
            )
            append = true
            receivedBytes += read
            emit(ModelDownloadState.Downloading(receivedBytes = receivedBytes, totalBytes = totalBytes))
        }

        emit(ModelDownloadState.Verifying)

        val actualHash = sha256(tempPath)
        if (actualHash == null) {
            deleteFile(tempPath)
            emit(ModelDownloadState.Failed("SHA-256 is unavailable on this platform."))
            return@flow
        }

        if (!actualHash.equals(expectedHash, ignoreCase = true)) {
            deleteFile(tempPath)
            deleteFile(destinationPath)
            emit(ModelDownloadState.Failed("SHA-256 verification failed."))
            return@flow
        }

        if (!moveFile(tempPath, destinationPath)) {
            emit(ModelDownloadState.Failed("Downloaded file could not be moved into place."))
            return@flow
        }

        emit(ModelDownloadState.Ready(destinationPath))
    }

    override fun cancel() = Unit

    private fun parseTotalBytes(
        contentRange: String?,
        contentLength: Long?,
        resumed: Boolean,
        partialBytes: Long,
    ): Long? {
        val rangeTotal = contentRange
            ?.substringAfter('/')
            ?.toLongOrNull()
        if (rangeTotal != null) return rangeTotal

        return contentLength?.let { if (resumed) it + partialBytes else it }
    }
}
