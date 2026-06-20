package com.wnl.cashchat.api.domain.shop.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * itemCode 구매 시 지급할 (grantItemCode, grantQty) 목록. 패키지는 여러 행, 단건도 자기 자신 1행.
 */
@Entity
@Table(
    name = "shop_item_grant",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_shop_item_grant_item_grant", columnNames = ["item_code", "grant_item_code"]),
    ],
)
class ShopItemGrant(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "item_code", nullable = false, length = 50)
    val itemCode: String,

    @Column(name = "grant_item_code", nullable = false, length = 50)
    val grantItemCode: String,

    @Column(name = "grant_qty", nullable = false)
    val grantQty: Int,
)
