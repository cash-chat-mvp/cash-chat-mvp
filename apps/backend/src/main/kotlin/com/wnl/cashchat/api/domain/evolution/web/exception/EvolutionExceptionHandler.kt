package com.wnl.cashchat.api.domain.evolution.web.exception

import com.wnl.cashchat.api.common.web.response.ErrorResponse
import com.wnl.cashchat.api.domain.economy.exception.FeatureDisabledException
import com.wnl.cashchat.api.domain.evolution.exception.EvolutionAttemptNotFoundException
import com.wnl.cashchat.api.domain.evolution.exception.EvolutionIdempotencyKeyRequiredException
import com.wnl.cashchat.api.domain.evolution.exception.EvolutionInsufficientExpException
import com.wnl.cashchat.api.domain.evolution.exception.EvolutionLevelMismatchException
import com.wnl.cashchat.api.domain.evolution.exception.EvolutionMaxLevelException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice(basePackages = ["com.wnl.cashchat.api.domain.evolution"])
class EvolutionExceptionHandler {
    @ExceptionHandler(EvolutionLevelMismatchException::class)
    fun mismatch(e: EvolutionLevelMismatchException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ErrorResponse("EVOLUTION_LEVEL_MISMATCH", e.message ?: "레벨 불일치"))

    @ExceptionHandler(EvolutionInsufficientExpException::class)
    fun insufficient(e: EvolutionInsufficientExpException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ErrorResponse("EVOLUTION_INSUFFICIENT_EXP", e.message ?: "경험치 부족"))

    @ExceptionHandler(EvolutionMaxLevelException::class)
    fun maxLevel(e: EvolutionMaxLevelException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ErrorResponse("EVOLUTION_MAX_LEVEL", e.message ?: "최대 레벨"))

    @ExceptionHandler(EvolutionAttemptNotFoundException::class)
    fun notFound(e: EvolutionAttemptNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse("EVOLUTION_ATTEMPT_NOT_FOUND", e.message ?: "시도 없음"))

    @ExceptionHandler(EvolutionIdempotencyKeyRequiredException::class)
    fun keyRequired(e: EvolutionIdempotencyKeyRequiredException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse("EVOLUTION_IDEMPOTENCY_KEY_REQUIRED", e.message ?: "Idempotency-Key 필요"))

    @ExceptionHandler(FeatureDisabledException::class)
    fun disabled(e: FeatureDisabledException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ErrorResponse("FEATURE_DISABLED", e.message ?: "기능 중지"))
}
