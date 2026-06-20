package com.wnl.cashchat.api.domain.shop.persistence.repository

import com.wnl.cashchat.api.domain.shop.persistence.entity.ShopItemGrant
import org.springframework.data.jpa.repository.JpaRepository

interface ShopItemGrantRepository : JpaRepository<ShopItemGrant, Long> {
    // itemCode 오름차순 grantItemCode 정렬 → UPSERT 락 순서 고정(데드락 방지)
    fun findByItemCodeOrderByGrantItemCodeAsc(itemCode: String): List<ShopItemGrant>
}
