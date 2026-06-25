package com.wnl.cashchat.api.domain.evolution.web.exception

import com.wnl.cashchat.api.common.web.response.ErrorResponse
import com.wnl.cashchat.api.domain.evolution.exception.AlreadyMaxLevelException
import com.wnl.cashchat.api.domain.evolution.exception.InsufficientEvolutionExpException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice(basePackages = ["com.wnl.cashchat.api.domain.evolution"])
class EvolutionExceptionHandler {

    @ExceptionHandler(AlreadyMaxLevelException::class)
    fun handleAlreadyMaxLevel(e: AlreadyMaxLevelException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse("ALREADY_MAX_LEVEL", e.message ?: "Already at max evolution level"))

    @ExceptionHandler(InsufficientEvolutionExpException::class)
    fun handleInsufficientEvolutionExp(e: InsufficientEvolutionExpException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse("INSUFFICIENT_EVOLUTION_EXP", e.message ?: "Insufficient evolution exp"))
}