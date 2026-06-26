package com.wnl.cashchat.api.domain.evolution.web.controller

import com.wnl.cashchat.api.common.security.jwt.JwtTokenHandler
import com.wnl.cashchat.api.domain.evolution.service.EvolutionAttemptRecordResult
import com.wnl.cashchat.api.domain.evolution.service.EvolutionService
import com.wnl.cashchat.api.domain.evolution.web.exception.EvolutionExceptionHandler
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

@WebMvcTest(EvolutionController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(EvolutionExceptionHandler::class)
class EvolutionControllerTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var mockMvc: MockMvc

    @MockBean lateinit var evolutionService: EvolutionService
    @MockBean lateinit var jwtTokenHandler: JwtTokenHandler
    @MockBean(name = "jpaMappingContext") lateinit var jpaMappingContext: JpaMetamodelMappingContext

    private val principal = UsernamePasswordAuthenticationToken(1L, null)

    init {
        test("GET /attempts returns records with ISO-8601 UTC attemptedAt") {
            whenever(evolutionService.getAttempts(eq(1L), any())).thenReturn(
                listOf(
                    EvolutionAttemptRecordResult(
                        success = true, fromLevel = 2, resultLevel = 3, cost = 1200,
                        attemptedAt = Instant.parse("2026-06-25T12:34:56Z"),
                    )
                )
            )

            mockMvc.perform(get("/api/evolution/attempts").principal(principal))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.attempts[0].success").value(true))
                .andExpect(jsonPath("$.attempts[0].fromLevel").value(2))
                .andExpect(jsonPath("$.attempts[0].resultLevel").value(3))
                .andExpect(jsonPath("$.attempts[0].cost").value(1200))
                .andExpect(jsonPath("$.attempts[0].attemptedAt").value("2026-06-25T12:34:56Z"))
        }

        test("GET /attempts with limit over 100 returns 400") {
            mockMvc.perform(get("/api/evolution/attempts").param("limit", "101").principal(principal))
                .andExpect(status().isBadRequest)
        }

        test("GET /attempts with limit 0 returns 400") {
            mockMvc.perform(get("/api/evolution/attempts").param("limit", "0").principal(principal))
                .andExpect(status().isBadRequest)
        }
    }
}
