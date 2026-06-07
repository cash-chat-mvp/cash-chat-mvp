package com.wnl.cashchat.api.domain.shop.persistence.repository

import com.wnl.cashchat.api.domain.shop.persistence.entity.ShopItem
import com.wnl.cashchat.api.domain.shop.persistence.entity.ShopItemCategory
import org.springframework.data.jpa.repository.JpaRepository

interface ShopItemRepository : JpaRepository<ShopItem, String> {
    fun findByCategoryAndIsActiveTrueOrderByDisplayOrderAsc(category: ShopItemCategory): List<ShopItem>
}
