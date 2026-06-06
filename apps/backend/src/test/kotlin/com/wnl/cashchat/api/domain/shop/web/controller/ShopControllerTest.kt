package com.wnl.cashchat.api.domain.shop.web.controller

import com.wnl.cashchat.api.common.security.jwt.JwtTokenHandler
import com.wnl.cashchat.api.domain.inventory.service.InventoryLine
import com.wnl.cashchat.api.domain.shop.exception.InsufficientCoinException
import com.wnl.cashchat.api.domain.shop.persistence.entity.PurchaseOrderStatus
import com.wnl.cashchat.api.domain.shop.persistence.entity.ShopItem
import com.wnl.cashchat.api.domain.shop.persistence.entity.ShopItemCategory
import com.wnl.cashchat.api.domain.shop.service.PurchaseResult
import com.wnl.cashchat.api.domain.shop.service.ShopCatalogService
import com.wnl.cashchat.api.domain.shop.service.ShopPurchaseFacade
import com.wnl.cashchat.api.domain.shop.web.exception.ShopExceptionHandler
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(ShopController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ShopExceptionHandler::class)
class ShopControllerTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var mockMvc: MockMvc

    @MockBean lateinit var shopCatalogService: ShopCatalogService
    @MockBean lateinit var shopPurchaseFacade: ShopPurchaseFacade
    @MockBean lateinit var jwtTokenHandler: JwtTokenHandler
    @MockBean(name = "jpaMappingContext") lateinit var jpaMappingContext: JpaMetamodelMappingContext

    private val principal = UsernamePasswordAuthenticationToken(1L, null)
    private val validUuid = "11111111-1111-1111-1111-111111111111"

    init {
        test("GET items ENHANCE returns phase1Active true and items") {
            whenever(shopCatalogService.listItems(ShopItemCategory.ENHANCE)).thenReturn(
                listOf(ShopItem("EVO_STONE", "진화석", ShopItemCategory.ENHANCE, 200L, "재료", true, 10))
            )
            mockMvc.perform(get("/api/shop/items").param("category", "ENHANCE").principal(principal))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.category").value("ENHANCE"))
                .andExpect(jsonPath("$.phase1Active").value(true))
                .andExpect(jsonPath("$.items[0].itemCode").value("EVO_STONE"))
                .andExpect(jsonPath("$.items[0].priceCoin").value(200))
        }

        test("GET items COSMETIC returns phase1Active false and empty items") {
            whenever(shopCatalogService.listItems(ShopItemCategory.COSMETIC)).thenReturn(emptyList())
            mockMvc.perform(get("/api/shop/items").param("category", "COSMETIC").principal(principal))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.phase1Active").value(false))
                .andExpect(jsonPath("$.items.length()").value(0))
        }

        test("GET items with invalid category returns 400 INVALID_CATEGORY") {
            mockMvc.perform(get("/api/shop/items").param("category", "FOO").principal(principal))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("INVALID_CATEGORY"))
        }

        test("POST purchase returns 200 with balance and inventory") {
            whenever(shopPurchaseFacade.purchase(eq(1L), any())).thenReturn(
                PurchaseResult(123L, PurchaseOrderStatus.COMPLETED, 1050L, listOf(InventoryLine("EVO_STONE", 3)))
            )
            mockMvc.perform(
                post("/api/shop/purchase").principal(principal)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"itemCode":"EVO_STONE","qty":1,"idempotencyKey":"$validUuid"}""")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.purchaseOrderId").value(123))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.coinBalance").value(1050))
                .andExpect(jsonPath("$.inventory[0].itemCode").value("EVO_STONE"))
                .andExpect(jsonPath("$.inventory[0].qty").value(3))
        }

        test("POST purchase with qty<1 returns 400 VALIDATION") {
            mockMvc.perform(
                post("/api/shop/purchase").principal(principal)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"itemCode":"EVO_STONE","qty":0,"idempotencyKey":"$validUuid"}""")
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("VALIDATION"))
        }

        test("POST purchase with non-UUID idempotencyKey returns 400 VALIDATION") {
            mockMvc.perform(
                post("/api/shop/purchase").principal(principal)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"itemCode":"EVO_STONE","qty":1,"idempotencyKey":"not-a-uuid"}""")
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("VALIDATION"))
        }

        test("POST purchase mapping INSUFFICIENT_COIN returns 400") {
            whenever(shopPurchaseFacade.purchase(eq(1L), any())).thenThrow(InsufficientCoinException())
            mockMvc.perform(
                post("/api/shop/purchase").principal(principal)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"itemCode":"EVO_STONE","qty":1,"idempotencyKey":"$validUuid"}""")
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_COIN"))
        }
    }
}
