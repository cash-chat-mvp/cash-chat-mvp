package com.wnl.cashchat.api.domain.ad.web.exception

import com.wnl.cashchat.api.common.web.response.ErrorResponse
import com.wnl.cashchat.api.domain.ad.exception.GoogleAdSsvTransientException
import com.wnl.cashchat.api.domain.ad.exception.InvalidGoogleAdSsvCallbackException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice(basePackages = ["com.wnl.cashchat.api.domain.ad"])
class GoogleAdSsvExceptionHandler {
    @ExceptionHandler(InvalidGoogleAdSsvCallbackException::class)
    fun handleInvalidCallback(exception: InvalidGoogleAdSsvCallbackException): ResponseEntity<ErrorResponse> {
        logger.warn("Invalid Google Ad SSV callback: {}", exception.message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse("INVALID_GOOGLE_AD_SSV_CALLBACK", "Invalid Google Ad SSV callback."))
    }

    @ExceptionHandler(GoogleAdSsvTransientException::class)
    fun handleTransientFailure(exception: GoogleAdSsvTransientException): ResponseEntity<ErrorResponse> {
        logger.warn("Google Ad SSV transient failure: {}", exception.message, exception)
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ErrorResponse("GOOGLE_AD_SSV_TEMPORARILY_UNAVAILABLE", "Google Ad SSV is temporarily unavailable."))
    }

    companion object {
        private val logger = LoggerFactory.getLogger(GoogleAdSsvExceptionHandler::class.java)
    }
}
