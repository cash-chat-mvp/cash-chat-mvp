package com.wnl.cashchat.api.domain.shop.web.request

import com.wnl.cashchat.api.domain.shop.service.PurchaseCommand
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class PurchaseRequest(
    @field:NotBlank
    val itemCode: String = "",

    @field:Min(1)
    val qty: Int = 0,

    // UUID 형식만 검증(버전 무관) — spec: "서버는 형식만 검증"
    @field:Pattern(
        regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
        message = "idempotencyKey must be a UUID",
    )
    val idempotencyKey: String = "",
) {
    fun toCommand() = PurchaseCommand(itemCode = itemCode, qty = qty, idempotencyKey = idempotencyKey)
}
