package com.wnl.cashchat.api.domain.shop.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 상점 카탈로그 아이템(참조/시드 데이터). itemCode 가 자연키 PK.
 * 운영자 관리 UI 는 범위 외 — 시드/마이그레이션으로만 변경한다.
 */
@Entity
@Table(name = "shop_item")
class ShopItem(
    @Id
    @Column(name = "item_code", length = 50)
    val itemCode: String,

    @Column(nullable = false, length = 100)
    val name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val category: ShopItemCategory,

    @Column(name = "price_coin", nullable = false)
    val priceCoin: Long,

    @Column(name = "effect_summary", nullable = false, length = 255)
    val effectSummary: String,

    @Column(name = "is_active", nullable = false)
    val isActive: Boolean,

    @Column(name = "display_order", nullable = false)
    val displayOrder: Int,
)
