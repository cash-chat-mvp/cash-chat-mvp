package com.wnl.cashchat.api.domain.point.exception

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

@ResponseStatus(HttpStatus.PAYMENT_REQUIRED)
class InsufficientPointsException(
    message: String = "Insufficient point balance"
) : RuntimeException(message)
