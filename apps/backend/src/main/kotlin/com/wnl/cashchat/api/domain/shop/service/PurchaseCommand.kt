package com.wnl.cashchat.api.domain.shop.service

data class PurchaseCommand(
    val itemCode: String,
    val qty: Int,
    val idempotencyKey: String,
)
