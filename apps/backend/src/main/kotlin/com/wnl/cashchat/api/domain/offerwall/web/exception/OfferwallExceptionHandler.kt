package com.wnl.cashchat.api.domain.offerwall.web.exception

import com.wnl.cashchat.api.common.web.response.ErrorResponse
import com.wnl.cashchat.api.domain.offerwall.exception.UnknownOfferwallPlatformException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice(basePackages = ["com.wnl.cashchat.api.domain.offerwall"])
class OfferwallExceptionHandler {
    private val log = LoggerFactory.getLogger(OfferwallExceptionHandler::class.java)

    @ExceptionHandler(UnknownOfferwallPlatformException::class)
    fun handleUnknownPlatform(e: UnknownOfferwallPlatformException): ResponseEntity<ErrorResponse> {
        log.warn("Unknown offerwall platform in callback path: {}", e.raw)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse("UNKNOWN_OFFERWALL_PLATFORM", "지원하지 않는 오퍼월 플랫폼입니다."))
    }
}
