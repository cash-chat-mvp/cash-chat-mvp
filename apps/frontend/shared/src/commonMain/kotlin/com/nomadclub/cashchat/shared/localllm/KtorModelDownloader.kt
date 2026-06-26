package com.nomadclub.cashchat.shared.localllm

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
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

        println("📥GemmaDL GET $resolvedUrl (partial=$partialBytes)")
        // prepareGet + execute = 본문을 메모리에 통째로 버퍼링하지 않고 디스크로 스트리밍한다.
        // client.get() 은 Darwin(NSURLSession)에서 본문 전체를 받을 때까지 반환하지 않아,
        // 2.5GB 다운로드 시 메모리 폭증 + 헤더 단계처럼 보이는 장시간 블로킹을 유발했다.
        // 연결/전송 정체는 client 의 connectTimeout(30s)/socketTimeout(120s)이 잡는다.
        client.prepareGet(resolvedUrl) {
            if (partialBytes > 0L) {
                header(HttpHeaders.Range, "bytes=$partialBytes-")
            }
        }.execute { response ->
            println("📥GemmaDL HTTP ${response.status.value} contentLength=${response.contentLength()}")

            if (!response.status.isSuccess()) {
                emit(ModelDownloadState.Failed("Download failed with HTTP ${response.status.value}."))
                return@execute
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
            println("📥GemmaDL 본문 스트림 시작 — 수신 루프 진입")
            val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
            var append = resumed
            var lastLoggedBytes = 0L

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
                // ~64MB 마다 한 번씩 진행 로그(콘솔에서 바이트 흐름 확인용 — 스팸 방지).
                if (receivedBytes - lastLoggedBytes >= 64L * 1024 * 1024) {
                    lastLoggedBytes = receivedBytes
                    println("📥GemmaDL 진행 ${receivedBytes / (1024 * 1024)}MB / ${totalBytes / (1024 * 1024)}MB")
                }
                emit(ModelDownloadState.Downloading(receivedBytes = receivedBytes, totalBytes = totalBytes))
            }

            println("📥GemmaDL 본문 수신 완료 received=$receivedBytes — 검증 시작")
            emit(ModelDownloadState.Verifying)

            val actualHash = sha256(tempPath)
            if (actualHash == null) {
                deleteFile(tempPath)
                emit(ModelDownloadState.Failed("SHA-256 is unavailable on this platform."))
                return@execute
            }

            if (!actualHash.equals(expectedHash, ignoreCase = true)) {
                deleteFile(tempPath)
                deleteFile(destinationPath)
                emit(ModelDownloadState.Failed("SHA-256 verification failed."))
                return@execute
            }

            if (!moveFile(tempPath, destinationPath)) {
                emit(ModelDownloadState.Failed("Downloaded file could not be moved into place."))
                return@execute
            }

            emit(ModelDownloadState.Ready(destinationPath))
        }
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
