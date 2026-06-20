package com.wnl.cashchat.api.domain.shop.persistence.repository

import com.wnl.cashchat.api.domain.shop.persistence.entity.PurchaseOrder
import org.springframework.data.jpa.repository.JpaRepository

interface PurchaseOrderRepository : JpaRepository<PurchaseOrder, Long> {
    fun findByUserIdAndIdempotencyKey(userId: Long, idempotencyKey: String): PurchaseOrder?
}
