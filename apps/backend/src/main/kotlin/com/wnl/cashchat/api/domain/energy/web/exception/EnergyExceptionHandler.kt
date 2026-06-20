package com.wnl.cashchat.api.domain.energy.web.exception

import com.wnl.cashchat.api.common.web.response.ErrorResponse
import com.wnl.cashchat.api.domain.energy.exception.InsufficientEnergyException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice(basePackages = ["com.wnl.cashchat.api.domain.energy"])
class EnergyExceptionHandler {

    @ExceptionHandler(InsufficientEnergyException::class)
    fun handleInsufficientEnergy(e: InsufficientEnergyException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse("INSUFFICIENT_ENERGY", e.message ?: "Not enough energy"))
}
