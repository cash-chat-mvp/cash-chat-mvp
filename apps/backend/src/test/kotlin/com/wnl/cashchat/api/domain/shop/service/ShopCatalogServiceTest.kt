package com.wnl.cashchat.api.domain.shop.service

import com.wnl.cashchat.api.domain.shop.persistence.entity.ShopItem
import com.wnl.cashchat.api.domain.shop.persistence.entity.ShopItemCategory
import com.wnl.cashchat.api.domain.shop.persistence.repository.ShopItemRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ShopCatalogServiceTest : FunSpec({
    lateinit var shopItemRepository: ShopItemRepository
    lateinit var service: ShopCatalogService

    beforeTest {
        shopItemRepository = mock()
        service = ShopCatalogService(shopItemRepository)
    }

    test("ENHANCE returns active items from repository") {
        whenever(
            shopItemRepository.findByCategoryAndIsActiveTrueOrderByDisplayOrderAsc(ShopItemCategory.ENHANCE)
        ).thenReturn(
            listOf(
                ShopItem("EVO_STONE", "진화석", ShopItemCategory.ENHANCE, 200L, "재료", true, 10),
            )
        )

        val items = service.listItems(ShopItemCategory.ENHANCE)

        items.map { it.itemCode } shouldBe listOf("EVO_STONE")
    }

    test("COSMETIC returns empty list (Phase 1 inactive category)") {
        whenever(
            shopItemRepository.findByCategoryAndIsActiveTrueOrderByDisplayOrderAsc(ShopItemCategory.COSMETIC)
        ).thenReturn(emptyList())

        service.listItems(ShopItemCategory.COSMETIC) shouldBe emptyList()
    }
})
