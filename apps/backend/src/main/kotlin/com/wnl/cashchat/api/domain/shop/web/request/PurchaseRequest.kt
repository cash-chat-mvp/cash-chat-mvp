package com.wnl.cashchat.api.domain.shop.web.request

import com.wnl.cashchat.api.domain.shop.service.PurchaseCommand
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class PurchaseRequest(
    @field:NotBlank
    val itemCode: String,

    // 상한은 1회 구매 합리적 한도이자 grant(grantQty * qty) Int 곱셈 오버플로 방지선
    @field:Min(1)
    @field:Max(9999)
    val qty: Int,

    // UUID 형식만 검증(버전 무관) — spec: "서버는 형식만 검증". NotBlank 로 누락/공백도 명확히 거부.
    @field:NotBlank
    @field:Pattern(
        regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
        message = "idempotencyKey must be a UUID",
    )
    val idempotencyKey: String,
) {
    fun toCommand() = PurchaseCommand(itemCode = itemCode, qty = qty, idempotencyKey = idempotencyKey)
}
