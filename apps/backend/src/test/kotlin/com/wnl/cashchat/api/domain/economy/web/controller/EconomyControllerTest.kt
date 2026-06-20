package com.wnl.cashchat.api.domain.economy.web.controller

import com.wnl.cashchat.api.common.security.jwt.JwtTokenHandler
import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.economy.exception.WalletNotInitializedException
import com.wnl.cashchat.api.domain.economy.persistence.entity.UserWallet
import com.wnl.cashchat.api.domain.economy.properties.EconomyProperties
import com.wnl.cashchat.api.domain.economy.service.WalletService
import com.wnl.cashchat.api.domain.economy.web.exception.EconomyExceptionHandler
import com.wnl.cashchat.api.domain.user.persistence.entity.Role
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
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

/**
 * 의도적으로 웹 슬라이스(@WebMvcTest) 테스트다 — 라우팅·응답 매핑·예외 매핑(상태코드/에러코드)만 검증한다.
 * 코드베이스 관례(ChatControllerTest/AttendanceControllerTest 와 동일: `addFilters = false` + `.principal(...)` 주입).
 * 실제 JWT/보안 필터 통과와 DB 적립·스냅샷 정합(EnergyService.grant → snapshot)은 Testcontainers MySQL 기반의
 * WalletPersistenceIntegrationTest·EnergyServiceIntegrationTest 에서 별도로 다룬다.
 */
@WebMvcTest(EconomyController::class, WalletController::class)
@AutoConfigureMockMvc(addFilters = false)
@EnableConfigurationProperties(EconomyProperties::class)
@Import(EconomyExceptionHandler::class)
class EconomyControllerTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var mockMvc: MockMvc

    @MockBean lateinit var walletService: WalletService
    @MockBean lateinit var jwtTokenHandler: JwtTokenHandler
    @MockBean(name = "jpaMappingContext") lateinit var jpaMappingContext: JpaMetamodelMappingContext

    private val principal = UsernamePasswordAuthenticationToken(1L, null)

    private fun walletWithEnergy(available: Long): UserWallet {
        val user = User(id = 1L, role = Role.MEMBER, provider = AuthProviderType.NONE, name = "me")
        return UserWallet(user = user).apply { grantEnergy(available, maxEnergy = 50) }
    }

    init {
        test("GET /economy/me returns the authenticated user's energy snapshot") {
            whenever(walletService.snapshot(1L)).thenReturn(walletWithEnergy(3))

            mockMvc.perform(get("/api/v1/economy/me").principal(principal))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.energy.available").value(3))
                .andExpect(jsonPath("$.energy.reserved").value(0))
                .andExpect(jsonPath("$.energy.max").value(50))
                .andExpect(jsonPath("$.point.pending").value(0))
                .andExpect(jsonPath("$.point.confirmed").value(0))
                .andExpect(jsonPath("$.evolution.level").value(1))
                .andExpect(jsonPath("$.features.rewardChatEnabled").value(true))
        }

        test("GET /economy/me returns 404 WALLET_NOT_FOUND when the wallet is missing") {
            whenever(walletService.snapshot(1L)).thenThrow(WalletNotInitializedException(1L))

            mockMvc.perform(get("/api/v1/economy/me").principal(principal))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.code").value("WALLET_NOT_FOUND"))
        }

        test("GET /economy/policy returns server policy values") {
            mockMvc.perform(get("/api/v1/economy/policy").principal(principal))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.maxEnergy").value(50))
                .andExpect(jsonPath("$.energyCostPerChat").value(1))
                .andExpect(jsonPath("$.chatRewardPt").value(1))
        }

        test("GET /wallet returns the wallet summary") {
            whenever(walletService.snapshot(1L)).thenReturn(walletWithEnergy(3))

            mockMvc.perform(get("/api/v1/wallet").principal(principal))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.energyAvailable").value(3))
                .andExpect(jsonPath("$.energyReserved").value(0))
                .andExpect(jsonPath("$.maxEnergy").value(50))
        }
    }
}
