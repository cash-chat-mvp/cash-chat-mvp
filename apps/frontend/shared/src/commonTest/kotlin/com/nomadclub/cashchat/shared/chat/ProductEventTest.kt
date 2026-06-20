package com.nomadclub.cashchat.shared.chat

import com.nomadclub.cashchat.shared.core.network.TokenProvider
import com.nomadclub.cashchat.shared.core.network.createCashChatHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private object NoAuthProvider : TokenProvider {
    override suspend fun accessToken(): String? = null
    override suspend fun refresh(): Boolean = false
}

/** ChatApiTest와 동일한 MockEngine 구성으로 SSE 응답만 바꾼다. */
private fun chatApiWithMockSse(sse: String): ChatApi {
    val engine = MockEngine {
        respond(sse, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/event-stream"))
    }
    return ChatApi(createCashChatHttpClient("https://api.test", NoAuthProvider, engine), "https://api.test")
}

class ProductEventTest {
    @Test
    fun `product 이벤트를 ProductCards로 파싱한다`() = runTest {
        val sse = "event:message\ndata:추천드려요\n\n" +
            "event:product\ndata:{\"products\":[{\"title\":\"버즈3\",\"price\":149000,\"rating\":4.7,\"reviewCount\":32000,\"imageUrl\":\"https://i\",\"trackingUrl\":\"https://t\"}]}\n\n"
        val api = chatApiWithMockSse(sse)
        val events = api.streamMessage(7, "hi").toList()
        val productEvent = events.filterIsInstance<ChatStreamEvent.ProductCards>().single()
        assertEquals("버즈3", productEvent.products.single().title)
    }
}
