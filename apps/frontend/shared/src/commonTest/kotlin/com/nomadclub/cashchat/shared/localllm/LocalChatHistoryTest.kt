package com.nomadclub.cashchat.shared.localllm

import com.nomadclub.cashchat.shared.chat.model.ChatItem
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalChatHistoryTest {

    @Test
    fun `save 와 load 는 user assistant 텍스트만 복원한다`() = runTest {
        val history = JsonFileLocalChatHistory(testHistoryPath("persist"))

        history.save(
            listOf(
                ChatItem.UserMessage("u1", "안녕", ChatItem.SendStatus.CONFIRMED),
                ChatItem.ProductCards("p1", emptyList()),
                ChatItem.AssistantMessage("a1", "반가워", isStreaming = false),
            ),
        )

        val loaded = history.load()
        assertEquals(2, loaded.size)

        val user = loaded[0] as ChatItem.UserMessage
        val assistant = loaded[1] as ChatItem.AssistantMessage
        assertEquals("안녕", user.text)
        assertEquals(ChatItem.SendStatus.CONFIRMED, user.status)
        assertEquals("반가워", assistant.text)
        assertEquals(false, assistant.isStreaming)
        assertEquals(false, assistant.isError)
    }

    @Test
    fun `clear 는 저장된 대화를 비운다`() = runTest {
        val path = testHistoryPath("clear")
        val history = JsonFileLocalChatHistory(path)
        history.save(listOf(ChatItem.UserMessage("u1", "질문", ChatItem.SendStatus.CONFIRMED)))

        history.clear()

        assertTrue(history.load().isEmpty())
    }

    @Test
    fun `load 는 알 수 없거나 손상된 role 행을 무시한다`() = runTest {
        val path = testHistoryPath("unknown-roles")
        writeTextFile(
            path,
            """
            [
              {"role":"user","text":"질문"},
              {"role":"assistant","text":"응답"},
              {"role":"system","text":"skip"},
              {"role":null,"text":"skip"},
              {"text":"skip"}
            ]
            """.trimIndent(),
        )
        val history = JsonFileLocalChatHistory(path)

        val loaded = history.load()

        assertEquals(2, loaded.size)
        assertEquals("질문", (loaded[0] as ChatItem.UserMessage).text)
        assertEquals("응답", (loaded[1] as ChatItem.AssistantMessage).text)
    }

    private fun testHistoryPath(name: String): String {
        val dir = "${localModelsDir().trimEnd('/')}/localllm-tests"
        ensureDirectory(dir)
        return "$dir/$name-${Random.nextInt().toUInt().toString(16)}.json"
    }
}
