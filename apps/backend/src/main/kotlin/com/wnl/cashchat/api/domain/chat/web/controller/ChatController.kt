package com.wnl.cashchat.api.domain.chat.web.controller

import com.wnl.cashchat.api.domain.chat.service.ChatService
import com.wnl.cashchat.api.domain.chat.web.request.ChatStreamRequest
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/api/v1/chat")
class ChatController(
    private val chatService: ChatService,
) {

    @PostMapping("/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stream(
        authentication: Authentication,
        @Valid @RequestBody request: ChatStreamRequest,
    ): SseEmitter {
        val userId = authentication.principal as? Long
            ?: throw IllegalArgumentException("Invalid authenticated principal")

        val emitter = SseEmitter(0L)

        chatService.stream(
            userId = userId,
            conversationId = request.conversationId!!,
            content = request.message,
        ).subscribe(
            { chunk -> emitter.send(SseEmitter.event().name("message").data(chunk)) },
            { error ->
                emitter.send(SseEmitter.event().name("error").data(error.message ?: "stream failed"))
                emitter.complete()
            },
            emitter::complete
        )

        return emitter
    }
}
