package com.wnl.cashchat.api.domain.attendance.exception

class InvalidAttendanceQueryException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
