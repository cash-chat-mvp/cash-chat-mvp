package com.wnl.cashchat.api.domain.roulette.web.exception

import com.wnl.cashchat.api.common.web.response.ErrorResponse
import com.wnl.cashchat.api.domain.roulette.exception.AdNotVerifiedException
import com.wnl.cashchat.api.domain.roulette.exception.DailyLimitReachedException
import com.wnl.cashchat.api.domain.roulette.exception.FreeSpinAvailableException
import com.wnl.cashchat.api.domain.roulette.exception.FreeSpinUsedException
import com.wnl.cashchat.api.domain.roulette.exception.NonceAlreadyUsedException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice(basePackages = ["com.wnl.cashchat.api.domain.roulette"])
class RouletteExceptionHandler {
    @ExceptionHandler(FreeSpinAvailableException::class)
    fun handleFreeSpinAvailable(e: FreeSpinAvailableException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse("FREE_SPIN_AVAILABLE", e.message ?: "Free spin available"))

    @ExceptionHandler(FreeSpinUsedException::class)
    fun handleFreeSpinUsed(e: FreeSpinUsedException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse("FREE_SPIN_USED", e.message ?: "Free spin already used"))

    @ExceptionHandler(DailyLimitReachedException::class)
    fun handleDailyLimitReached(e: DailyLimitReachedException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse("DAILY_LIMIT_REACHED", e.message ?: "Daily limit reached"))

    @ExceptionHandler(AdNotVerifiedException::class)
    fun handleAdNotVerified(e: AdNotVerifiedException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse("AD_NOT_VERIFIED", e.message ?: "Ad not verified"))

    @ExceptionHandler(NonceAlreadyUsedException::class)
    fun handleNonceAlreadyUsed(e: NonceAlreadyUsedException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse("NONCE_ALREADY_USED", e.message ?: "Nonce already used"))
}
