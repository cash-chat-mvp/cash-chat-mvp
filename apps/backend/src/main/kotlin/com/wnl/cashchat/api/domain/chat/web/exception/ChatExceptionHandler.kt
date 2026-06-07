package com.wnl.cashchat.api.domain.chat.web.exception

import com.wnl.cashchat.api.common.web.response.ErrorResponse
import com.wnl.cashchat.api.domain.chat.exception.ConversationAccessDeniedException
import com.wnl.cashchat.api.domain.chat.exception.ConversationNotFoundException
import com.wnl.cashchat.api.domain.energy.exception.InsufficientEnergyException
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

    /**
     * 밥 게이트: 채팅 연료(밥)가 부족하면 409 Conflict 를 반환한다.
     * 클라이언트는 광고 시청 등으로 밥을 충전한 뒤 재시도해야 한다.
     */
    @ExceptionHandler(InsufficientEnergyException::class)
    fun handleInsufficientEnergyException(e: InsufficientEnergyException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse("INSUFFICIENT_ENERGY", e.message ?: "Not enough energy"))
}
