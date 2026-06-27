package com.nomadclub.cashchat.shared.localllm

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class KtorModelDownloaderTest {

    @Test
    fun `unresolved URL 은 요청 전에 실패한다`() = runTest {
        var requestCount = 0
        val client = HttpClient(
            MockEngine {
                requestCount += 1
                respond("", HttpStatusCode.OK)
            },
        )

        try {
            val downloader = KtorModelDownloader(client)
            val states = downloader.download(
                spec = DEFAULT_GEMMA_SPEC.copy(url = UNRESOLVED_GEMMA_MODEL_URL, sha256 = "abc123"),
                destinationPath = testDownloadPath("unresolved-url"),
            ).toList()

            assertEquals(0, requestCount)
            assertEquals(1, states.size)
            assertEquals("Model URL is unresolved.", assertIs<ModelDownloadState.Failed>(states.single()).reason)
        } finally {
            client.close()
        }
    }

    private fun testDownloadPath(name: String): String {
        val dir = "${localModelsDir().trimEnd('/')}/localllm-tests"
        ensureDirectory(dir)
        return "$dir/$name.bin"
    }
}
