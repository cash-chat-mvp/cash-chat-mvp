package com.wnl.cashchat.api.domain.shop.exception

class ItemNotFoundException(
    message: String = "Shop item not found",
) : RuntimeException(message)
