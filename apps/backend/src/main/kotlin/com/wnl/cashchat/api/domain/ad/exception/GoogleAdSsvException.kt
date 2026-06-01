package com.wnl.cashchat.api.domain.ad.exception

sealed class GoogleAdSsvException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class InvalidGoogleAdSsvCallbackException(
    message: String,
    cause: Throwable? = null,
) : GoogleAdSsvException(message, cause)

class GoogleAdSsvTransientException(
    message: String,
    cause: Throwable? = null,
) : GoogleAdSsvException(message, cause)
