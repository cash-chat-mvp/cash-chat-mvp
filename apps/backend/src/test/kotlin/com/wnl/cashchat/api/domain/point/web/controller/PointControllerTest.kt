package com.wnl.cashchat.api.domain.point.web.controller

import com.wnl.cashchat.api.common.security.jwt.JwtTokenHandler
import com.wnl.cashchat.api.domain.point.service.UserPointService
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

@WebMvcTest(PointController::class)
@AutoConfigureMockMvc(addFilters = false)
class PointControllerTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var mockMvc: MockMvc

    @MockBean lateinit var userPointService: UserPointService
    @MockBean lateinit var jwtTokenHandler: JwtTokenHandler
    @MockBean(name = "jpaMappingContext") lateinit var jpaMappingContext: JpaMetamodelMappingContext

    private val principal = UsernamePasswordAuthenticationToken(1L, null)

    init {
        test("GET /api/points/me returns the user's balance") {
            whenever(userPointService.getBalance(eq(1L))).thenReturn(1350L)

            mockMvc.perform(get("/api/points/me").principal(principal))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.balance").value(1350))
        }

        test("GET /api/points/me returns zero for an uninitialized user") {
            whenever(userPointService.getBalance(eq(1L))).thenReturn(0L)

            mockMvc.perform(get("/api/points/me").principal(principal))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.balance").value(0))
        }
    }
}
