package com.wnl.cashchat.api.domain.evolution.web.controller

import com.wnl.cashchat.api.common.security.jwt.JwtTokenHandler
import com.wnl.cashchat.api.domain.economy.exception.FeatureDisabledException
import com.wnl.cashchat.api.domain.evolution.exception.EvolutionAttemptNotFoundException
import com.wnl.cashchat.api.domain.evolution.exception.EvolutionInsufficientExpException
import com.wnl.cashchat.api.domain.evolution.exception.EvolutionLevelMismatchException
import com.wnl.cashchat.api.domain.evolution.exception.EvolutionMaxLevelException
import com.wnl.cashchat.api.domain.evolution.persistence.entity.EvolutionAttempt
import com.wnl.cashchat.api.domain.evolution.persistence.entity.EvolutionResult
import com.wnl.cashchat.api.domain.evolution.service.EvolutionMe
import com.wnl.cashchat.api.domain.evolution.service.EvolutionService
import com.wnl.cashchat.api.domain.evolution.web.exception.EvolutionExceptionHandler
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
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

/**
 * 웹 슬라이스(@WebMvcTest) — 라우팅·응답 매핑·예외 매핑(상태코드/에러코드)·Idempotency-Key 헤더 처리만 검증.
 * 코드베이스 관례(EconomyControllerTest 동일: addFilters=false + .principal(...)).
 * 진화 도메인 로직·멱등·RNG·정산 정합은 EvolutionServiceTest(Testcontainers)에서 별도 검증.
 */
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

    private fun successAttempt(): EvolutionAttempt = EvolutionAttempt(
        id = 10L, userId = 1L, attemptKey = "key-1",
        levelBefore = 1, levelAfter = 2, requiredExp = 30, baseSuccessRate = 0.80,
        failStackBefore = 0, finalSuccessRate = 0.80, rollValue = 0.5,
        result = EvolutionResult.SUCCESS, expAfter = 0, failStackAfter = 0, policyVersion = 1,
    )

    init {
        test("POST /evolution/attempts returns 201 with result") {
            whenever(evolutionService.attempt(eq(1L), eq("key-1"), eq(1))).thenReturn(successAttempt())

            mockMvc.perform(
                post("/api/v1/evolution/attempts")
                    .principal(principal)
                    .header("Idempotency-Key", "key-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"expectedLevel":1}"""),
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.levelAfter").value(2))
                .andExpect(jsonPath("$.finalSuccessRate").value(0.80))
        }

        test("POST /evolution/attempts without Idempotency-Key returns 400") {
            mockMvc.perform(
                post("/api/v1/evolution/attempts")
                    .principal(principal)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"expectedLevel":1}"""),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("EVOLUTION_IDEMPOTENCY_KEY_REQUIRED"))
        }

        test("POST /evolution/attempts maps level mismatch to 422") {
            whenever(evolutionService.attempt(eq(1L), eq("k"), eq(2)))
                .thenThrow(EvolutionLevelMismatchException(2, 1))

            mockMvc.perform(
                post("/api/v1/evolution/attempts")
                    .principal(principal).header("Idempotency-Key", "k")
                    .contentType(MediaType.APPLICATION_JSON).content("""{"expectedLevel":2}"""),
            )
                .andExpect(status().isUnprocessableEntity)
                .andExpect(jsonPath("$.code").value("EVOLUTION_LEVEL_MISMATCH"))
        }

        test("POST /evolution/attempts maps insufficient exp to 422") {
            whenever(evolutionService.attempt(eq(1L), eq("k"), eq(1)))
                .thenThrow(EvolutionInsufficientExpException(30, 10))

            mockMvc.perform(
                post("/api/v1/evolution/attempts")
                    .principal(principal).header("Idempotency-Key", "k")
                    .contentType(MediaType.APPLICATION_JSON).content("""{"expectedLevel":1}"""),
            )
                .andExpect(status().isUnprocessableEntity)
                .andExpect(jsonPath("$.code").value("EVOLUTION_INSUFFICIENT_EXP"))
        }

        test("POST /evolution/attempts maps max level to 422 EVOLUTION_MAX_LEVEL") {
            whenever(evolutionService.attempt(eq(1L), eq("k"), eq(5)))
                .thenThrow(EvolutionMaxLevelException(5))

            mockMvc.perform(
                post("/api/v1/evolution/attempts")
                    .principal(principal).header("Idempotency-Key", "k")
                    .contentType(MediaType.APPLICATION_JSON).content("""{"expectedLevel":5}"""),
            )
                .andExpect(status().isUnprocessableEntity)
                .andExpect(jsonPath("$.code").value("EVOLUTION_MAX_LEVEL"))
        }

        test("POST /evolution/attempts maps feature disabled to 503") {
            whenever(evolutionService.attempt(eq(1L), eq("k"), eq(1)))
                .thenThrow(FeatureDisabledException("disabled"))

            mockMvc.perform(
                post("/api/v1/evolution/attempts")
                    .principal(principal).header("Idempotency-Key", "k")
                    .contentType(MediaType.APPLICATION_JSON).content("""{"expectedLevel":1}"""),
            )
                .andExpect(status().isServiceUnavailable)
                .andExpect(jsonPath("$.code").value("FEATURE_DISABLED"))
        }

        test("GET /evolution/me returns 200 snapshot") {
            whenever(evolutionService.me(1L)).thenReturn(
                EvolutionMe(
                    level = 1, exp = 30, failStack = 0, maxLevel = 5,
                    requiredExp = 30, baseSuccessRate = 0.80, finalSuccessRate = 0.80, canAttempt = true,
                ),
            )

            mockMvc.perform(get("/api/v1/evolution/me").principal(principal))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.level").value(1))
                .andExpect(jsonPath("$.exp").value(30))
                .andExpect(jsonPath("$.maxLevel").value(5))
                .andExpect(jsonPath("$.canAttempt").value(true))
        }

        test("GET /evolution/attempts/{id} maps not found to 404") {
            whenever(evolutionService.findAttempt(1L, 99L)).thenThrow(EvolutionAttemptNotFoundException(99L))

            mockMvc.perform(get("/api/v1/evolution/attempts/99").principal(principal))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.code").value("EVOLUTION_ATTEMPT_NOT_FOUND"))
        }
    }
}
