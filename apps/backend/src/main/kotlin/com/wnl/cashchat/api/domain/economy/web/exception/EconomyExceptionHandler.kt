package com.wnl.cashchat.api.domain.economy.web.exception

import com.wnl.cashchat.api.common.web.response.ErrorResponse
import com.wnl.cashchat.api.domain.economy.exception.EnergyCapExceededException
import com.wnl.cashchat.api.domain.economy.exception.WalletNotInitializedException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice(basePackages = ["com.wnl.cashchat.api.domain.economy"])
class EconomyExceptionHandler {
    @ExceptionHandler(EnergyCapExceededException::class)
    fun handleCap(e: EnergyCapExceededException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ErrorResponse("ENERGY_CAP_EXCEEDED", e.message ?: "Energy 상한 초과"))

    @ExceptionHandler(WalletNotInitializedException::class)
    fun handleNotInit(e: WalletNotInitializedException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse("WALLET_NOT_FOUND", e.message ?: "지갑이 초기화되지 않았습니다."))
}
