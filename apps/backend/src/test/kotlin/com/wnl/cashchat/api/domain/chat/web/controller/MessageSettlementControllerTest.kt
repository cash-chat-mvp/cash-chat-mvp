package com.wnl.cashchat.api.domain.chat.web.controller

import com.wnl.cashchat.api.common.security.jwt.JwtTokenHandler
import com.wnl.cashchat.api.domain.chat.persistence.entity.SettlementStatus
import com.wnl.cashchat.api.domain.chat.service.ChatRewardSettlementService
import com.wnl.cashchat.api.domain.chat.web.exception.ChatExceptionHandler
import com.wnl.cashchat.api.domain.chat.web.response.MessageSettlementResponse
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

@WebMvcTest(MessageSettlementController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ChatExceptionHandler::class)
class MessageSettlementControllerTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var mockMvc: MockMvc

    @MockBean lateinit var settlementService: ChatRewardSettlementService
    @MockBean lateinit var jwtTokenHandler: JwtTokenHandler
    @MockBean(name = "jpaMappingContext") lateinit var jpaMappingContext: JpaMetamodelMappingContext

    private val principal = UsernamePasswordAuthenticationToken(1L, null)

    init {
        test("GET /messages/{messageId}/settlement returns 200 with settlement data") {
            whenever(settlementService.findForUser(1L, "msg_1")).thenReturn(
                MessageSettlementResponse(
                    messageId = "msg_1",
                    chatStatus = "COMPLETED",
                    settlementStatus = SettlementStatus.SETTLED,
                    energyDelta = -1L,
                    pendingCashablePtDelta = 1L,
                    evolutionExpDelta = 1L,
                    settledAt = Instant.parse("2026-06-21T00:00:00Z"),
                )
            )

            mockMvc.perform(get("/api/v1/messages/msg_1/settlement").principal(principal))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.settlementStatus").value("SETTLED"))
                .andExpect(jsonPath("$.energyDelta").value(-1))
                .andExpect(jsonPath("$.pendingCashablePtDelta").value(1))
                .andExpect(jsonPath("$.chatStatus").value("COMPLETED"))
        }

        test("GET /messages/{messageId}/settlement returns 404 SETTLEMENT_NOT_FOUND when missing") {
            whenever(settlementService.findForUser(1L, "missing")).thenReturn(null)

            mockMvc.perform(get("/api/v1/messages/missing/settlement").principal(principal))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.code").value("SETTLEMENT_NOT_FOUND"))
        }
    }
}
