package com.wnl.cashchat.api.domain.chat.web.exception

import com.wnl.cashchat.api.common.web.response.ErrorResponse
import com.wnl.cashchat.api.domain.chat.exception.ConversationAccessDeniedException
import com.wnl.cashchat.api.domain.chat.exception.ConversationNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice(basePackages = ["com.wnl.cashchat.api.domain.chat"])
class ChatExceptionHandler {

    @ExceptionHandler(ConversationNotFoundException::class)
    fun handleConversationNotFoundException(e: ConversationNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse("CONVERSATION_NOT_FOUND", "Conversation not found"))

    @ExceptionHandler(ConversationAccessDeniedException::class)
    fun handleConversationAccessDeniedException(e: ConversationAccessDeniedException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse("CONVERSATION_ACCESS_DENIED", "Conversation does not belong to user"))
}
