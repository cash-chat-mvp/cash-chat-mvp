package com.wnl.cashchat.api.domain.chat.web.controller

import com.wnl.cashchat.api.domain.chat.service.ChatService
import com.wnl.cashchat.api.domain.chat.web.request.ChatStreamRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody as OpenApiRequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import reactor.core.Disposable

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
            ApiResponse(responseCode = "401", description = "Authentication is required.")
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
    ): SseEmitter {
        val userId = authentication.principal as? Long
            ?: throw IllegalArgumentException("Invalid authenticated principal")

        val emitter = SseEmitter(SSE_TIMEOUT_MILLIS)

        val subscription = chatService.stream(
            userId = userId,
            conversationId = request.conversationId!!,
            content = request.message,
        ).subscribe(
            { chunk ->
                emitter.send(SseEmitter.event().name("message").data(chunk))
            },
            {
                runCatching {
                    emitter.send(SseEmitter.event().name("error").data(STREAM_FAILED_MESSAGE))
                }
                emitter.complete()
            },
            emitter::complete
        )

        emitter.onCompletion { dispose(subscription) }
        emitter.onTimeout {
            dispose(subscription)
            emitter.complete()
        }
        emitter.onError { dispose(subscription) }

        return emitter
    }

    private fun dispose(subscription: Disposable) {
        if (!subscription.isDisposed) {
            subscription.dispose()
        }
    }

    companion object {
        private const val SSE_TIMEOUT_MILLIS = 300_000L
        private const val STREAM_FAILED_MESSAGE = "stream failed"
    }
}
