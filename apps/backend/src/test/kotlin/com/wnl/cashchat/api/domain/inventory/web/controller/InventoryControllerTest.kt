package com.wnl.cashchat.api.domain.inventory.web.controller

import com.wnl.cashchat.api.common.security.jwt.JwtTokenHandler
import com.wnl.cashchat.api.domain.inventory.service.InventoryLine
import com.wnl.cashchat.api.domain.inventory.service.InventoryService
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(InventoryController::class)
@AutoConfigureMockMvc(addFilters = false)
class InventoryControllerTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var mockMvc: MockMvc

    @MockBean lateinit var inventoryService: InventoryService
    @MockBean lateinit var jwtTokenHandler: JwtTokenHandler
    @MockBean(name = "jpaMappingContext") lateinit var jpaMappingContext: JpaMetamodelMappingContext

    private val principal = UsernamePasswordAuthenticationToken(1L, null)

    init {
        test("GET /api/inventory/me returns owned items") {
            whenever(inventoryService.getMine(eq(1L))).thenReturn(
                listOf(InventoryLine("EVO_STONE", 2), InventoryLine("PROTECT_TICKET", 1))
            )
            mockMvc.perform(get("/api/inventory/me").principal(principal))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items[0].itemCode").value("EVO_STONE"))
                .andExpect(jsonPath("$.items[0].qty").value(2))
                .andExpect(jsonPath("$.items[1].itemCode").value("PROTECT_TICKET"))
                .andExpect(jsonPath("$.items[1].qty").value(1))
        }

        test("GET /api/inventory/me returns empty items when user has no inventory") {
            whenever(inventoryService.getMine(eq(1L))).thenReturn(emptyList())
            mockMvc.perform(get("/api/inventory/me").principal(principal))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items.length()").value(0))
        }
    }
}
