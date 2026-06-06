package com.wnl.cashchat.api.domain.shop.web.response

import com.wnl.cashchat.api.domain.shop.persistence.entity.ShopItem
import com.wnl.cashchat.api.domain.shop.persistence.entity.ShopItemCategory

data class ShopCatalogResponse(
    val category: String,
    val phase1Active: Boolean,
    val items: List<Item>,
) {
    data class Item(
        val itemCode: String,
        val name: String,
        val priceCoin: Long,
        val effectSummary: String,
        val displayOrder: Int,
    )

    companion object {
        fun of(category: ShopItemCategory, items: List<ShopItem>) = ShopCatalogResponse(
            category = category.name,
            phase1Active = category.phase1Active,
            items = items.map {
                Item(
                    itemCode = it.itemCode,
                    name = it.name,
                    priceCoin = it.priceCoin,
                    effectSummary = it.effectSummary,
                    displayOrder = it.displayOrder,
                )
            },
        )
    }
}
