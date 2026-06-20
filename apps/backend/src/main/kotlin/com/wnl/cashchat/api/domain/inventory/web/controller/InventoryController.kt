package com.wnl.cashchat.api.domain.inventory.web.controller

import com.wnl.cashchat.api.common.security.userId
import com.wnl.cashchat.api.domain.inventory.service.InventoryService
import com.wnl.cashchat.api.domain.inventory.web.response.InventoryResponse
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/inventory")
class InventoryController(
    private val inventoryService: InventoryService,
) {
    @GetMapping("/me")
    fun getMine(authentication: Authentication): InventoryResponse =
        InventoryResponse.from(inventoryService.getMine(authentication.userId()))
}
