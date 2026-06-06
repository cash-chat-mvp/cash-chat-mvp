package com.wnl.cashchat.api.domain.inventory.web.response

import com.wnl.cashchat.api.domain.inventory.service.InventoryLine

data class InventoryResponse(
    val items: List<Item>,
) {
    data class Item(val itemCode: String, val qty: Int)

    companion object {
        fun from(lines: List<InventoryLine>) = InventoryResponse(
            items = lines.map { Item(it.itemCode, it.qty) },
        )
    }
}
