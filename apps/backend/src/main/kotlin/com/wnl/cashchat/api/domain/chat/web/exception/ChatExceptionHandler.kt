package com.wnl.cashchat.api.domain.chat.web.exception

import com.wnl.cashchat.api.common.web.response.ErrorResponse
import com.wnl.cashchat.api.domain.chat.exception.ConversationAccessDeniedException
import com.wnl.cashchat.api.domain.chat.exception.ConversationNotFoundException
import com.wnl.cashchat.api.domain.chat.exception.RewardAlreadySettledException
import com.wnl.cashchat.api.domain.economy.exception.EnergyInsufficientException
import com.wnl.cashchat.api.domain.economy.exception.FeatureDisabledException
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

    @ExceptionHandler(EnergyInsufficientException::class)
    fun handleEnergyInsufficientException(e: EnergyInsufficientException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ErrorResponse("ENERGY_INSUFFICIENT", e.message ?: "Not enough energy to start a chat"))

    @ExceptionHandler(FeatureDisabledException::class)
    fun handleFeatureDisabledException(e: FeatureDisabledException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ErrorResponse("FEATURE_DISABLED", e.message ?: "Feature is currently disabled"))

    @ExceptionHandler(RewardAlreadySettledException::class)
    fun handleRewardAlreadySettledException(e: RewardAlreadySettledException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse("REWARD_ALREADY_SETTLED", e.message ?: "Reward already settled for this message"))
}
