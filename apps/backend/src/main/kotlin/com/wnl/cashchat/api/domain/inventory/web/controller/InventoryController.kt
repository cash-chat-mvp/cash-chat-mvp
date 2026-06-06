package com.wnl.cashchat.api.domain.inventory.web.controller

import com.wnl.cashchat.api.domain.inventory.service.InventoryService
import com.wnl.cashchat.api.domain.inventory.web.response.InventoryResponse
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
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

    private fun Authentication.userId(): Long =
        principal as? Long ?: throw AuthenticationCredentialsNotFoundException("Invalid authenticated principal")
}
