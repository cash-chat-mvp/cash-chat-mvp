package com.wnl.cashchat.api.domain.chat.web.controller

import com.wnl.cashchat.api.common.web.response.ErrorResponse
import com.wnl.cashchat.api.domain.chat.service.ChatService
import com.wnl.cashchat.api.domain.chat.web.request.ChatStreamRequest
import com.wnl.cashchat.api.domain.chat.web.response.ChatHistoryResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
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
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.security.core.Authentication
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
@Tag(name = "Chat", description = "Chat streaming endpoints")
class ChatController(
    private val chatService: ChatService,
) {

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
            ApiResponse(responseCode = "400", description = "The request body is invalid."),
            ApiResponse(responseCode = "401", description = "Authentication is required."),
            ApiResponse(responseCode = "402", description = "The user does not have enough points.")
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
        val userId = authentication.principal as? Long
            ?: throw IllegalArgumentException("Invalid authenticated principal")

        return chatService.stream(
            userId = userId,
            conversationId = request.conversationId!!,
            content = request.message,
        )
            .map { chunk -> ServerSentEvent.builder<String>(chunk).event(MESSAGE_EVENT).build() }
            .onErrorResume {
                Flux.just(ServerSentEvent.builder<String>(STREAM_FAILED_MESSAGE).event(ERROR_EVENT).build())
            }
    }

    companion object {
        private const val MESSAGE_EVENT = "message"
        private const val ERROR_EVENT = "error"
        private const val STREAM_FAILED_MESSAGE = "stream failed"
    }
}
