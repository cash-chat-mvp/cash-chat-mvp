package com.wnl.cashchat.api.domain.shop.persistence.entity

/**
 * 상점 카테고리. Phase 1 은 ENHANCE 만 활성(phase1Active=true).
 * COSMETIC/VOUCHER 는 enum 범위에는 있으나 Phase 1 카탈로그 비노출.
 */
enum class ShopItemCategory {
    ENHANCE,
    COSMETIC,
    VOUCHER,
    ;

    val phase1Active: Boolean
        get() = this == ENHANCE
}
