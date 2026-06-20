package com.nomadclub.cashchat.shared.chat

import com.nomadclub.cashchat.shared.core.network.ApiException
import com.nomadclub.cashchat.shared.core.network.createCashChatHttpClient
import com.nomadclub.cashchat.shared.core.network.TokenProvider
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private object NoAuth : TokenProvider {
    override suspend fun accessToken(): String? = null
    override suspend fun refresh(): Boolean = false
}

private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

class ChatApiTest {

    @Test
    fun `대화방을 생성한다`() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/api/v1/chat/conversations", request.url.encodedPath)
            respond(
                """{"conversationId":7,"title":"영어 공부 팁","createdAt":"2026-06-10T00:00:00Z","updatedAt":"2026-06-10T00:00:00Z"}""",
                HttpStatusCode.OK, jsonHeaders,
            )
        }
        val api = ChatApi(createCashChatHttpClient("https://api.test", NoAuth, engine), "https://api.test")
        val conversation = api.createConversation("영어 공부 팁")
        assertEquals(7L, conversation.conversationId)
    }

    @Test
    fun `스트림은 message 토큰을 Flow로 흘린다`() = runTest {
        val sse = "event:message\ndata:안녕\n\nevent:message\ndata:하세요\n\n"
        val engine = MockEngine {
            respond(sse, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/event-stream"))
        }
        val api = ChatApi(createCashChatHttpClient("https://api.test", NoAuth, engine), "https://api.test")
        val events = api.streamMessage(conversationId = 7, message = "hi").toList()
        assertEquals(listOf<ChatStreamEvent>(ChatStreamEvent.Token("안녕"), ChatStreamEvent.Token("하세요"), ChatStreamEvent.Done), events)
    }

    @Test
    fun `스트림 시작 전 409는 ApiException으로 던진다`() = runTest {
        val engine = MockEngine {
            respond("""{"code":"INSUFFICIENT_ENERGY","message":"x"}""", HttpStatusCode.Conflict, jsonHeaders)
        }
        val api = ChatApi(createCashChatHttpClient("https://api.test", NoAuth, engine), "https://api.test")
        val exception = assertFailsWith<ApiException> { api.streamMessage(7, "hi").toList() }
        assertEquals(ApiException.INSUFFICIENT_ENERGY, exception.code)
    }

    @Test
    fun `done 이벤트는 토큰으로 렌더하지 않고 정상 종료한다`() = runTest {
        // PR #189(CC-311): 백엔드가 정상 종료 시 event:done / data:[DONE] 전송.
        // 이를 토큰으로 처리하면 말풍선에 "[DONE]"이 붙으므로 무시하고 Done 1회만 방출해야 한다.
        val sse = "event:message\ndata:안녕\n\nevent:done\ndata:[DONE]\n\n"
        val engine = MockEngine {
            respond(sse, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/event-stream"))
        }
        val api = ChatApi(createCashChatHttpClient("https://api.test", NoAuth, engine), "https://api.test")
        val events = api.streamMessage(7, "hi").toList()
        assertEquals(listOf<ChatStreamEvent>(ChatStreamEvent.Token("안녕"), ChatStreamEvent.Done), events)
    }

    @Test
    fun `error 이벤트는 StreamError로 매핑된다`() = runTest {
        val sse = "event:message\ndata:부분\n\nevent:error\ndata:stream failed\n\n"
        val engine = MockEngine {
            respond(sse, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/event-stream"))
        }
        val api = ChatApi(createCashChatHttpClient("https://api.test", NoAuth, engine), "https://api.test")
        val events = api.streamMessage(7, "hi").toList()
        assertTrue(events.contains(ChatStreamEvent.StreamError("stream failed")))
    }
}
