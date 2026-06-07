package com.wnl.cashchat.api.domain.shop.exception

class InsufficientCoinException(
    message: String = "Insufficient coin balance",
) : RuntimeException(message)
