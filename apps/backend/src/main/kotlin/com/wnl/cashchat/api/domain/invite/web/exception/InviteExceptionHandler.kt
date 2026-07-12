package com.wnl.cashchat.api.domain.invite.web.exception

import com.wnl.cashchat.api.common.web.response.ErrorResponse
import com.wnl.cashchat.api.domain.invite.exception.AlreadyRedeemedException
import com.wnl.cashchat.api.domain.invite.exception.InvalidCodeException
import com.wnl.cashchat.api.domain.invite.exception.NotEligibleException
import com.wnl.cashchat.api.domain.invite.exception.SelfReferralException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice(basePackages = ["com.wnl.cashchat.api.domain.invite"])
class InviteExceptionHandler {

    @ExceptionHandler(AlreadyRedeemedException::class)
    fun handleAlreadyRedeemed(e: AlreadyRedeemedException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse("ALREADY_REDEEMED", e.message ?: "Already redeemed"))

    @ExceptionHandler(InvalidCodeException::class)
    fun handleInvalidCode(e: InvalidCodeException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse("INVALID_CODE", e.message ?: "Invalid code"))

    @ExceptionHandler(SelfReferralException::class)
    fun handleSelfReferral(e: SelfReferralException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse("SELF_REFERRAL", e.message ?: "Self referral not allowed"))

    @ExceptionHandler(NotEligibleException::class)
    fun handleNotEligible(e: NotEligibleException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse("NOT_ELIGIBLE", e.message ?: "Not eligible"))
}
