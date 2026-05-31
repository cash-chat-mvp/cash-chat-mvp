package com.wnl.cashchat.api.domain.attendance.exception

class AlreadyCheckedInException(
    message: String = "Already checked in today",
) : RuntimeException(message)
