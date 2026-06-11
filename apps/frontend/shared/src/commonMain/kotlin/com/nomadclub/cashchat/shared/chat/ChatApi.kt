package com.nomadclub.cashchat.shared.chat

import com.nomadclub.cashchat.shared.chat.model.ChatMessageDto
import com.nomadclub.cashchat.shared.chat.model.ChatStreamRequest
import com.nomadclub.cashchat.shared.chat.model.ConversationDto
import com.nomadclub.cashchat.shared.chat.model.ConversationSummaryDto
import com.nomadclub.cashchat.shared.chat.model.CreateConversationRequest
import com.nomadclub.cashchat.shared.chat.model.ProductDto
import com.nomadclub.cashchat.shared.core.network.SseParser
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** SSE 스트림 이벤트. 시작 전 HTTP 에러는 Flow가 아니라 ApiException으로 전파된다. */
sealed interface ChatStreamEvent {
    data class Token(val text: String) : ChatStreamEvent
    data class StreamError(val message: String) : ChatStreamEvent
    data object Done : ChatStreamEvent

    /** `event: product` — 쿠팡 상품 카드 (P2-2) */
    data class ProductCards(val products: List<ProductDto>) : ChatStreamEvent

    /** `event: gate` — Ad Gate 블라인드 (P2-3) */
    data class Gate(val teaserChars: Int, val rewardCoin: Int) : ChatStreamEvent
}

@Serializable
private data class ProductPayload(val products: List<ProductDto>)

@Serializable
private data class GatePayload(val teaserChars: Int = 80, val rewardCoin: Int = 30)

private val sseJson = Json { ignoreUnknownKeys = true }

class ChatApi(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    @Throws(Exception::class)
    suspend fun createConversation(title: String? = null): ConversationDto =
        client.post("$baseUrl/api/v1/chat/conversations") {
            contentType(ContentType.Application.Json)
            setBody(CreateConversationRequest(title))
        }.body()

    @Throws(Exception::class)
    suspend fun listConversations(): List<ConversationSummaryDto> =
        client.get("$baseUrl/api/v1/chat/conversations").body()

    @Throws(Exception::class)
    suspend fun getMessages(conversationId: Long): List<ChatMessageDto> =
        client.get("$baseUrl/api/v1/chat/conversations/$conversationId/messages").body()

    /** SSE 스트림. message 토큰 → Token, error 이벤트 → StreamError, 정상 종료 → Done. */
    fun streamMessage(conversationId: Long, message: String): Flow<ChatStreamEvent> = flow {
        client.preparePost("$baseUrl/api/v1/chat/stream") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Text.EventStream)
            setBody(ChatStreamRequest(conversationId, message))
        }.execute { response ->
            val channel = response.bodyAsChannel()
            val parser = SseParser()
            var errored = false
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                val event = parser.feed(line) ?: continue
                when (event.event) {
                    "error" -> { errored = true; emit(ChatStreamEvent.StreamError(event.data)) }
                    "product" -> emit(ChatStreamEvent.ProductCards(sseJson.decodeFromString<ProductPayload>(event.data).products))
                    "gate" -> {
                        val gate = sseJson.decodeFromString<GatePayload>(event.data)
                        emit(ChatStreamEvent.Gate(gate.teaserChars, gate.rewardCoin))
                    }
                    else -> emit(ChatStreamEvent.Token(event.data))
                }
            }
            if (!errored) emit(ChatStreamEvent.Done)
        }
    }
}
