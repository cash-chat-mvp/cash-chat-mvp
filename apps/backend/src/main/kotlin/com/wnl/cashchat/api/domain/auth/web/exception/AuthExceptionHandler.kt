package com.wnl.cashchat.api.domain.auth.web.exception

import com.wnl.cashchat.api.common.web.response.ErrorResponse
import com.wnl.cashchat.api.domain.auth.exception.AlreadyOAuthUserException
import com.wnl.cashchat.api.domain.auth.exception.InvalidTokenException
import com.wnl.cashchat.api.domain.auth.exception.OAuthException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice(basePackages = ["com.wnl.cashchat.api.domain.auth"])
class AuthExceptionHandler {

    private val log = LoggerFactory.getLogger(AuthExceptionHandler::class.java)

    @ExceptionHandler(OAuthException::class)
    fun handleOAuthException(e: OAuthException): ResponseEntity<ErrorResponse> {
        log.error("OAuth external API error: {}", e.message, e.cause)
        return ResponseEntity
            .status(HttpStatus.BAD_GATEWAY)
            .body(ErrorResponse("OAUTH_EXTERNAL_ERROR", "OAuth 외부 서비스 오류가 발생했습니다."))
    }

    @ExceptionHandler(AlreadyOAuthUserException::class)
    fun handleAlreadyOAuthUserException(e: AlreadyOAuthUserException): ResponseEntity<ErrorResponse> {
        log.warn("Already OAuth user: {}", e.message)
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ErrorResponse("ALREADY_OAUTH_USER", e.message ?: "이미 OAuth로 가입된 사용자입니다."))
    }

    @ExceptionHandler(InvalidTokenException::class)
    fun handleInvalidTokenException(e: InvalidTokenException): ResponseEntity<ErrorResponse> {
        log.warn("Invalid token: {}", e.message)
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse("INVALID_TOKEN", e.message ?: "유효하지 않은 토큰입니다."))
    }
}
