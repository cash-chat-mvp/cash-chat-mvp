package com.wnl.cashchat.api.domain.shop.exception

class IdempotencyKeyConflictException(
    message: String = "Idempotency key reused with a different payload",
) : RuntimeException(message)
