package com.wnl.cashchat.api.domain.point.web.exception

import com.wnl.cashchat.api.common.web.response.ErrorResponse
import com.wnl.cashchat.api.domain.point.exception.InsufficientPointsException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class PointExceptionHandler {

    @ExceptionHandler(InsufficientPointsException::class)
    fun handleInsufficientPointsException(e: InsufficientPointsException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
            .body(ErrorResponse("INSUFFICIENT_POINTS", e.message ?: "Insufficient point balance"))
}
