package com.wnl.cashchat.api.domain.roulette.web.controller

import com.wnl.cashchat.api.common.security.jwt.JwtTokenHandler
import com.wnl.cashchat.api.domain.roulette.exception.FreeSpinAvailableException
import com.wnl.cashchat.api.domain.roulette.persistence.entity.RouletteAdNonce
import com.wnl.cashchat.api.domain.roulette.persistence.entity.RoulettePrize
import com.wnl.cashchat.api.domain.roulette.service.RouletteSegment
import com.wnl.cashchat.api.domain.roulette.service.RouletteService
import com.wnl.cashchat.api.domain.roulette.service.RouletteSpinResult
import com.wnl.cashchat.api.domain.roulette.service.RouletteStatus
import com.wnl.cashchat.api.domain.roulette.web.exception.RouletteExceptionHandler
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.LocalDate

@WebMvcTest(RouletteController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(RouletteExceptionHandler::class)
class RouletteControllerTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var mockMvc: MockMvc

    @MockitoBean lateinit var rouletteService: RouletteService
    @MockitoBean lateinit var jwtTokenHandler: JwtTokenHandler
    @MockitoBean lateinit var jpaMetamodelMappingContext: JpaMetamodelMappingContext

    private val principal = UsernamePasswordAuthenticationToken(1L, null)
    private val status = RouletteStatus(
        date = LocalDate.of(2026, 6, 21),
        dailyLimit = 5,
        spinsUsedToday = 1,
        freeSpinAvailable = false,
        remaining = 4,
        resetAtKst = Instant.parse("2026-06-21T15:00:00Z"),
        segments = listOf(RouletteSegment(0, RoulettePrize.JACKPOT_100, 100)),
    )

    init {
        test("status returns roulette state and segments") {
            whenever(rouletteService.statusOf(eq(1L), any())).thenReturn(status)

            mockMvc.perform(get("/api/roulette/status").principal(principal))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.date").value("2026-06-21"))
                .andExpect(jsonPath("$.dailyLimit").value(5))
                .andExpect(jsonPath("$.remaining").value(4))
                .andExpect(jsonPath("$.segments[0].prize").value("JACKPOT_100"))
        }

        test("spin returns prize energy, actual award, energy after, and updated status") {
            whenever(rouletteService.spinFree(eq(1L), any())).thenReturn(
                RouletteSpinResult(
                    prize = RoulettePrize.E10,
                    segmentIndex = 3,
                    prizeEnergy = 10,
                    awardedEnergy = 2,
                    energyAfter = 50,
                    status = status,
                )
            )

            mockMvc.perform(post("/api/roulette/spin").principal(principal))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.prize").value("E10"))
                .andExpect(jsonPath("$.segmentIndex").value(3))
                .andExpect(jsonPath("$.prizeEnergy").value(10))
                .andExpect(jsonPath("$.awardedEnergy").value(2))
                .andExpect(jsonPath("$.energyAfter").value(50))
        }

        test("issue nonce maps free spin available to conflict") {
            whenever(rouletteService.issueNonce(eq(1L), any())).thenThrow(FreeSpinAvailableException())

            mockMvc.perform(post("/api/roulette/issue-nonce").principal(principal))
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.code").value("FREE_SPIN_AVAILABLE"))
        }

        test("spin with ad sends nonce request body") {
            whenever(rouletteService.spinWithAd(eq(1L), eq("nonce-1"), any())).thenReturn(
                RouletteSpinResult(
                    prize = RoulettePrize.MISS,
                    segmentIndex = 2,
                    prizeEnergy = 0,
                    awardedEnergy = 0,
                    energyAfter = 40,
                    status = status,
                )
            )

            mockMvc.perform(
                post("/api/roulette/spin-with-ad")
                    .principal(principal)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"nonce":"nonce-1"}""")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.prize").value("MISS"))
        }

        test("issue nonce returns nonce and expiry") {
            whenever(rouletteService.issueNonce(eq(1L), any())).thenReturn(
                RouletteAdNonce("nonce-2", userId = 1L, expiresAt = Instant.parse("2026-06-21T03:10:00Z"))
            )

            mockMvc.perform(post("/api/roulette/issue-nonce").principal(principal))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.nonce").value("nonce-2"))
                .andExpect(jsonPath("$.expiresAt").exists())
        }
    }
}
