package com.wnl.cashchat.api.domain.inventory.persistence.entity

import com.wnl.cashchat.api.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * 사용자 보유 아이템 수량. (user_id, item_code) 복합 유니크.
 * 적재(grant)는 UserInventoryRepository.upsertQty 네이티브 UPSERT 로 동시성 안전하게 처리한다.
 */
@Entity
@Table(
    name = "user_inventory",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_user_inventory_user_item", columnNames = ["user_id", "item_code"]),
    ],
)
class UserInventory(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "item_code", nullable = false, length = 50)
    val itemCode: String,

    @Column(nullable = false)
    val qty: Int,
) : BaseEntity()
