package com.wnl.cashchat.api.domain.shop.exception

class ItemInactiveException(
    message: String = "Shop item is inactive",
) : RuntimeException(message)
