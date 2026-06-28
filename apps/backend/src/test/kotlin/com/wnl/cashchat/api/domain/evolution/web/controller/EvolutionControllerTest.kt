package com.wnl.cashchat.api.domain.evolution.web.controller

import com.wnl.cashchat.api.common.security.jwt.JwtTokenHandler
import com.wnl.cashchat.api.domain.evolution.service.EvolutionAttemptRecordResult
import com.wnl.cashchat.api.domain.evolution.service.EvolutionService
import com.wnl.cashchat.api.domain.evolution.service.EvolutionStateResult
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
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
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
    @MockBean lateinit var timingSessionStore: com.wnl.cashchat.api.domain.evolution.service.TimingSessionStore
    @MockBean lateinit var timingConfig: com.wnl.cashchat.api.domain.evolution.properties.EvolutionProperties.TimingConfig
    @MockBean lateinit var jwtTokenHandler: JwtTokenHandler
    @MockBean(name = "jpaMappingContext") lateinit var jpaMappingContext: JpaMetamodelMappingContext

    private val principal = UsernamePasswordAuthenticationToken(1L, null)

    init {
        test("GET /me serializes the max-level flag as isMaxLevel (not maxLevel)") {
            // Jackson 이 Kotlin boolean 의 is 접두사를 떼어 maxLevel 로 직렬화하면
            // 프론트(kotlinx.serialization, isMaxLevel 필수)가 역직렬화에 실패한다(CC-352).
            whenever(evolutionService.getState(eq(1L))).thenReturn(
                EvolutionStateResult(
                    level = 1, isMaxLevel = false,
                    nextAttemptCost = 500, nextSuccessRate = 0.7, currentExp = 0,
                )
            )

            mockMvc.perform(get("/api/evolution/me").principal(principal))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.isMaxLevel").value(false))
                .andExpect(jsonPath("$.maxLevel").doesNotExist())
        }

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

        test("POST /timing-sessions returns session window params") {
            val started = Instant.parse("2026-06-26T00:00:00Z")
            whenever(timingSessionStore.issue(eq(1L))).thenReturn(
                com.wnl.cashchat.api.domain.evolution.service.TimingSession(
                    sessionId = "sess-1", userId = 1L,
                    serverStartedAt = started, expiresAt = started.plusSeconds(120),
                )
            )
            whenever(timingConfig.minimumHoldMs).thenReturn(600)
            whenever(timingConfig.cycleDurationMs).thenReturn(1800)

            mockMvc.perform(post("/api/evolution/timing-sessions").principal(principal))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.sessionId").value("sess-1"))
                .andExpect(jsonPath("$.serverStartedAt").value("2026-06-26T00:00:00Z"))
                .andExpect(jsonPath("$.minimumHoldMs").value(600))
                .andExpect(jsonPath("$.cycleDurationMs").value(1800))
        }

        test("POST /attempt with timing returns judged rates") {
            whenever(evolutionService.attempt(eq(1L), eq("key-1"), any())).thenReturn(
                com.wnl.cashchat.api.domain.evolution.service.EvolutionAttemptResult(
                    success = true, fromLevel = 2, resultLevel = 3, cost = 1200,
                    timingGrade = com.wnl.cashchat.api.domain.evolution.service.TimingGrade.PERFECT,
                    timingBonusRate = 0.10, baseSuccessRate = 0.65, finalSuccessRate = 0.75,
                )
            )
            val body = """{"idempotencyKey":"key-1","timing":{"sessionId":"s1","releasedAtMs":900}}"""
            mockMvc.perform(
                post("/api/evolution/attempt").principal(principal)
                    .contentType(MediaType.APPLICATION_JSON).content(body)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.timingGrade").value("PERFECT"))
                .andExpect(jsonPath("$.finalSuccessRate").value(0.75))
        }

        test("POST /attempt with blank idempotencyKey returns 400 INVALID_PARAMETER") {
            val body = """{"idempotencyKey":""}"""
            mockMvc.perform(
                post("/api/evolution/attempt").principal(principal)
                    .contentType(MediaType.APPLICATION_JSON).content(body)
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
        }

        test("POST /attempt with invalid timing session returns 422 INVALID_TIMING_SESSION") {
            whenever(evolutionService.attempt(eq(1L), eq("key-2"), any()))
                .thenThrow(com.wnl.cashchat.api.domain.evolution.exception.InvalidTimingSessionException())
            val body = """{"idempotencyKey":"key-2","timing":{"sessionId":"bad","releasedAtMs":900}}"""
            mockMvc.perform(
                post("/api/evolution/attempt").principal(principal)
                    .contentType(MediaType.APPLICATION_JSON).content(body)
            )
                .andExpect(status().isUnprocessableEntity)
                .andExpect(jsonPath("$.code").value("INVALID_TIMING_SESSION"))
        }
    }
}
