package com.wnl.cashchat.api.domain.chat.web.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.wnl.cashchat.api.common.web.response.ErrorResponse
import com.wnl.cashchat.api.domain.chat.service.ChatService
import com.wnl.cashchat.api.domain.chat.service.ChatStreamEvent
import com.wnl.cashchat.api.domain.chat.web.request.CreateConversationRequest
import com.wnl.cashchat.api.domain.chat.web.request.ChatStreamRequest
import com.wnl.cashchat.api.domain.chat.web.response.ChatHistoryResponse
import com.wnl.cashchat.api.domain.chat.web.response.ChatMessageResponse
import com.wnl.cashchat.api.domain.chat.web.response.ConversationResponse
import com.wnl.cashchat.api.domain.chat.web.response.ConversationSummaryResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody as OpenApiRequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.ServerSentEvent
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import java.util.UUID

/**
 * Exposes server-sent event endpoints for chat responses.
 */
@RestController
@RequestMapping("/api/v1/chat")
@Tag(name = "Chat", description = "Chat conversation and streaming endpoints")
class ChatController(
    private val chatService: ChatService,
    private val objectMapper: ObjectMapper,
) {

    @PostMapping("/conversations")
    @Operation(
        summary = "Create a chat conversation",
        description = "Creates a new chat room for the authenticated user. The frontend usually calls this before the first stream request."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Conversation created successfully.",
                content = [Content(schema = Schema(implementation = ConversationResponse::class))]
            ),
            ApiResponse(
                responseCode = "401",
                description = "Authentication is required.",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            )
        ]
    )
    fun createConversation(
        authentication: Authentication,
        @OpenApiRequestBody(
            required = false,
            description = "Optional conversation title, usually derived from the first user message.",
            content = [Content(schema = Schema(implementation = CreateConversationRequest::class))]
        )
        @RequestBody(required = false) request: CreateConversationRequest?,
    ): ConversationResponse =
        chatService.createConversation(
            userId = authentication.userId(),
            title = request?.title,
        )

    @GetMapping("/conversations")
    @Operation(
        summary = "List chat conversations",
        description = "Returns the authenticated user's chat rooms ordered by most recent activity."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Conversation list returned successfully.",
                content = [
                    Content(
                        array = ArraySchema(schema = Schema(implementation = ConversationSummaryResponse::class))
                    )
                ]
            ),
            ApiResponse(
                responseCode = "401",
                description = "Authentication is required.",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            )
        ]
    )
    fun listConversations(authentication: Authentication): List<ConversationSummaryResponse> =
        chatService.listConversations(userId = authentication.userId())

    @GetMapping("/conversations/{conversationId}/messages")
    @Operation(
        summary = "Get conversation messages",
        description = "Returns the selected chat room's persisted message history."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Conversation messages returned successfully.",
                content = [
                    Content(
                        array = ArraySchema(schema = Schema(implementation = ChatMessageResponse::class))
                    )
                ]
            ),
            ApiResponse(
                responseCode = "401",
                description = "Authentication is required.",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "404",
                description = "Conversation not found, or it belongs to another user.",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            )
        ]
    )
    fun getMessages(
        authentication: Authentication,
        @PathVariable
        @Parameter(description = "Conversation identifier.", example = "7")
        conversationId: Long,
    ): List<ChatMessageResponse> =
        chatService.getMessages(
            userId = authentication.userId(),
            conversationId = conversationId,
        )

    /**
     * Returns persisted chat history for the authenticated user's conversation.
     */
    @GetMapping("/history/{uuid}")
    @Operation(
        summary = "Get chat history",
        description = "Returns persisted messages for an authenticated user's conversation."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Chat history returned successfully.",
                content = [Content(schema = Schema(implementation = ChatHistoryResponse::class))]
            ),
            ApiResponse(
                responseCode = "400",
                description = "The supplied conversation UUID is malformed.",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "401",
                description = "Authentication is required.",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "403",
                description = "The conversation belongs to another user.",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "404",
                description = "Conversation not found.",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            )
        ]
    )
    fun history(
        authentication: Authentication,
        @PathVariable
        @Parameter(
            description = "Public conversation UUID.",
            example = "0c4fe408-6d7c-4bd9-b0f8-5fdbe2a6a6e8"
        )
        uuid: UUID,
    ): ResponseEntity<ChatHistoryResponse> {
        val userId = authentication.principal as? Long
            ?: throw IllegalArgumentException("Invalid authenticated principal")

        return ResponseEntity.ok(ChatHistoryResponse.from(chatService.getHistory(userId, uuid)))
    }

    /**
     * Starts a chat response stream for the authenticated user.
     */
    @PostMapping("/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    @Operation(
        summary = "Stream a chat completion",
        description = "Streams assistant output as server-sent events for the requested conversation."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "SSE stream opened successfully.",
                content = [
                    Content(
                        mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                        schema = Schema(
                            type = "string",
                            example = "event: message\ndata: Hello back\n\n"
                        )
                    )
                ]
            ),
            ApiResponse(
                responseCode = "400",
                description = "The request body is invalid.",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ErrorResponse::class)
                    )
                ]
            ),
            ApiResponse(
                responseCode = "401",
                description = "Authentication is required.",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ErrorResponse::class)
                    )
                ]
            ),
            ApiResponse(
                responseCode = "402",
                description = "The user does not have enough points.",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ErrorResponse::class)
                    )
                ]
            ),
            ApiResponse(
                responseCode = "404",
                description = "Conversation not found, or it belongs to another user.",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ErrorResponse::class)
                    )
                ]
            )
        ]
    )
    fun stream(
        authentication: Authentication,
        @OpenApiRequestBody(
            required = true,
            description = "Conversation identifier and the user message to stream a reply for.",
            content = [Content(schema = Schema(implementation = ChatStreamRequest::class))]
        )
        @Valid @RequestBody request: ChatStreamRequest,
    ): Flux<ServerSentEvent<String>> {
        return chatService.stream(
            userId = authentication.userId(),
            conversationId = request.conversationId!!,
            messageId = request.messageId,
            content = request.message,
        )
            .map { event ->
                when (event) {
                    is ChatStreamEvent.Meta -> sse("meta", objectMapper.writeValueAsString(event))
                    is ChatStreamEvent.Delta -> sse("delta", objectMapper.writeValueAsString(mapOf("text" to event.text)))
                    is ChatStreamEvent.RewardSettled -> sse("reward_settled", objectMapper.writeValueAsString(event.result))
                    is ChatStreamEvent.Done -> sse("done", objectMapper.writeValueAsString(mapOf("finishReason" to event.finishReason)))
                }
            }
            .onErrorResume {
                Flux.just(sse(ERROR_EVENT, STREAM_FAILED_MESSAGE))
            }
    }

    companion object {
        private const val ERROR_EVENT = "error"
        private const val STREAM_FAILED_MESSAGE = "stream failed"
    }

    private fun sse(event: String, data: String): ServerSentEvent<String> =
        ServerSentEvent.builder<String>(data).event(event).build()

    private fun Authentication.userId(): Long =
        principal as? Long ?: throw IllegalArgumentException("Invalid authenticated principal")
}
