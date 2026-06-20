package com.wnl.cashchat.api.domain.shop.web.response

import com.wnl.cashchat.api.domain.shop.service.PurchaseResult

data class PurchaseResponse(
    val purchaseOrderId: Long,
    val status: String,
    val coinBalance: Long,
    val inventory: List<Item>,
) {
    data class Item(val itemCode: String, val qty: Int)

    companion object {
        fun from(result: PurchaseResult) = PurchaseResponse(
            purchaseOrderId = result.purchaseOrderId,
            status = result.status.name,
            coinBalance = result.coinBalance,
            inventory = result.inventory.map { Item(it.itemCode, it.qty) },
        )
    }
}
