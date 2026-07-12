package com.nomadclub.cashchat.shared.localllm

import com.nomadclub.cashchat.shared.chat.model.ChatItem
import kotlin.random.Random
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LocalChatStoreTest {

    @Test
    fun `sendMessage 는 엔진을 지연 로드하고 assistant 토큰을 누적 저장한다`() = runTest {
        val spec = testSpec("success")
        val directory = testDirectory("success")
        writeFileBytes(modelFilePath(spec, directory), byteArrayOf(1))

        val engine = FakeLocalLlmEngine().apply {
            generator = {
                flow {
                    emit("안녕")
                    emit("!")
                }
            }
        }
        val history = JsonFileLocalChatHistory(testHistoryPath("success"))
        val store = LocalChatStore(
            engine = engine,
            history = history,
            scope = this,
            modelSpec = spec,
            modelDirectory = directory,
        )

        store.sendMessage("질문")
        testScheduler.advanceUntilIdle()
        store.sendMessage("다음 질문")
        testScheduler.advanceUntilIdle()

        val items = store.items.value
        val assistant = items.last() as ChatItem.AssistantMessage
        assertEquals(1, engine.loadCalls.size)
        assertEquals(listOf("질문", "다음 질문"), engine.prompts)
        assertEquals("안녕!", assistant.text)
        assertEquals(false, assistant.isStreaming)
        assertEquals(4, history.load().size)
    }

    @Test
    fun `비취소 예외는 assistant 를 error 로 마감한다`() = runTest {
        val spec = testSpec("error")
        val directory = testDirectory("error")
        writeFileBytes(modelFilePath(spec, directory), byteArrayOf(1))

        val engine = FakeLocalLlmEngine().apply {
            generator = {
                flow {
                    emit("부분")
                    throw IllegalStateException("boom")
                }
            }
        }
        val store = LocalChatStore(
            engine = engine,
            history = JsonFileLocalChatHistory(testHistoryPath("error")),
            scope = this,
            modelSpec = spec,
            modelDirectory = directory,
        )

        store.sendMessage("질문")
        testScheduler.advanceUntilIdle()

        val assistant = store.items.value.last()
        assistant as ChatItem.AssistantMessage
        assertEquals("부분", assistant.text)
        assertEquals(true, assistant.isError)
        assertEquals(false, assistant.isStreaming)
    }

    @Test
    fun `stop 은 부분 응답을 남기고 error 로 표시하지 않는다`() = runTest {
        val spec = testSpec("stop")
        val directory = testDirectory("stop")
        writeFileBytes(modelFilePath(spec, directory), byteArrayOf(1))

        val engine = FakeLocalLlmEngine().apply {
            generator = {
                flow {
                    emit("부분")
                    awaitCancellation()
                }
            }
        }
        val store = LocalChatStore(
            engine = engine,
            history = JsonFileLocalChatHistory(testHistoryPath("stop")),
            scope = this,
            modelSpec = spec,
            modelDirectory = directory,
        )

        store.sendMessage("질문")
        testScheduler.runCurrent()
        store.stop()
        testScheduler.advanceUntilIdle()

        val assistant = assertIs<ChatItem.AssistantMessage>(store.items.value.last())
        assertEquals("부분", assistant.text)
        assertEquals(false, assistant.isStreaming)
        assertEquals(false, assistant.isError)
        assertEquals(false, store.isStreaming.value)
    }

    @Test
    fun `clear 는 대화와 히스토리를 비우고 엔진 세션을 리셋한다`() = runTest {
        val spec = testSpec("clear")
        val directory = testDirectory("clear")
        writeFileBytes(modelFilePath(spec, directory), byteArrayOf(1))

        val history = JsonFileLocalChatHistory(testHistoryPath("clear"))
        val engine = FakeLocalLlmEngine().apply {
            generator = { flow { emit("응답") } }
        }
        val store = LocalChatStore(
            engine = engine,
            history = history,
            scope = this,
            modelSpec = spec,
            modelDirectory = directory,
        )

        store.sendMessage("질문")
        testScheduler.advanceUntilIdle()
        store.clear()
        testScheduler.advanceUntilIdle()

        assertTrue(store.items.value.isEmpty())
        assertTrue(history.load().isEmpty())
        assertEquals(1, engine.resetCalls)
    }

    private fun testSpec(name: String) = DEFAULT_GEMMA_SPEC.copy(
        fileName = "$name-${Random.nextInt().toUInt().toString(16)}.litertlm",
        sha256 = "UNRESOLVED_GEMMA_MODEL_SHA256",
        sizeBytes = 1L,
    )

    private fun testDirectory(name: String): String {
        val directory = "${localModelsDir().trimEnd('/')}/localllm-tests/$name-${Random.nextInt().toUInt().toString(16)}"
        ensureDirectory(directory)
        return directory
    }

    private fun testHistoryPath(name: String): String {
        val directory = "${localModelsDir().trimEnd('/')}/localllm-tests"
        ensureDirectory(directory)
        return "$directory/$name-${Random.nextInt().toUInt().toString(16)}.json"
    }
}
