package com.wnl.cashchat.api.domain.shop.service

import com.wnl.cashchat.api.domain.inventory.service.InventoryLine
import com.wnl.cashchat.api.domain.shop.persistence.entity.PurchaseOrderStatus

data class PurchaseResult(
    val purchaseOrderId: Long,
    val status: PurchaseOrderStatus,
    val coinBalance: Long,
    val inventory: List<InventoryLine>,
)
