package com.wnl.cashchat.api.domain.shop.web.controller

import com.wnl.cashchat.api.common.security.userId
import com.wnl.cashchat.api.domain.shop.persistence.entity.ShopItemCategory
import com.wnl.cashchat.api.domain.shop.service.ShopCatalogService
import com.wnl.cashchat.api.domain.shop.service.ShopPurchaseFacade
import com.wnl.cashchat.api.domain.shop.web.request.PurchaseRequest
import com.wnl.cashchat.api.domain.shop.web.response.PurchaseResponse
import com.wnl.cashchat.api.domain.shop.web.response.ShopCatalogResponse
import jakarta.validation.Valid
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/shop")
class ShopController(
    private val shopCatalogService: ShopCatalogService,
    private val shopPurchaseFacade: ShopPurchaseFacade,
) {
    @GetMapping("/items")
    fun items(@RequestParam category: ShopItemCategory): ShopCatalogResponse =
        ShopCatalogResponse.from(category, shopCatalogService.listItems(category))

    @PostMapping("/purchase")
    fun purchase(
        authentication: Authentication,
        @Valid @RequestBody request: PurchaseRequest,
    ): PurchaseResponse =
        PurchaseResponse.from(shopPurchaseFacade.purchase(authentication.userId(), request.toCommand()))
}
