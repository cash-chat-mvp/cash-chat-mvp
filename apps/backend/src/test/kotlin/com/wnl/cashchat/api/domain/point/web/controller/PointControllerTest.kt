package com.wnl.cashchat.api.domain.point.web.controller

import com.wnl.cashchat.api.common.security.jwt.JwtTokenHandler
import com.wnl.cashchat.api.domain.point.persistence.entity.PointTransaction
import com.wnl.cashchat.api.domain.point.persistence.entity.PointTransactionReason
import com.wnl.cashchat.api.domain.point.service.UserPointService
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
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

        test("GET /api/points/history returns items with page metadata") {
            val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "id"))
            val txn = PointTransaction(
                userId = 1L,
                delta = 100L,
                balanceAfter = 1350L,
                reason = PointTransactionReason.ATTENDANCE,
                idempotencyKey = "key-1",
            )
            whenever(userPointService.getHistory(eq(1L), any())).thenReturn(PageImpl(listOf(txn), pageable, 53L))

            mockMvc.perform(get("/api/points/history").principal(principal))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[0].delta").value(100))
                .andExpect(jsonPath("$.content[0].balanceAfter").value(1350))
                .andExpect(jsonPath("$.content[0].reason").value("ATTENDANCE"))
                .andExpect(jsonPath("$.content[0].createdAt").exists())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(53))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.hasNext").value(true))
        }

        test("GET /api/points/history requests id DESC sort and respects custom page/size") {
            val captor = argumentCaptor<Pageable>()
            whenever(userPointService.getHistory(eq(1L), captor.capture()))
                .thenReturn(PageImpl(emptyList(), PageRequest.of(2, 10, Sort.by(Sort.Direction.DESC, "id")), 0L))

            mockMvc.perform(
                get("/api/points/history").param("page", "2").param("size", "10").principal(principal)
            ).andExpect(status().isOk)

            val used = captor.firstValue
            used.pageNumber shouldBe 2
            used.pageSize shouldBe 10
            used.sort shouldBe Sort.by(Sort.Direction.DESC, "id")
        }

        test("GET /api/points/history clamps size above the maximum to 100") {
            val captor = argumentCaptor<Pageable>()
            whenever(userPointService.getHistory(eq(1L), captor.capture()))
                .thenReturn(PageImpl(emptyList(), PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "id")), 0L))

            mockMvc.perform(
                get("/api/points/history").param("size", "999").principal(principal)
            ).andExpect(status().isOk)

            captor.firstValue.pageSize shouldBe 100
        }

        test("GET /api/points/history returns hasNext false on the last page") {
            whenever(userPointService.getHistory(eq(1L), any()))
                .thenReturn(PageImpl(emptyList(), PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "id")), 0L))

            mockMvc.perform(get("/api/points/history").principal(principal))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.totalElements").value(0))
        }
    }
}
