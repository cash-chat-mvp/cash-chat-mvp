package com.wnl.cashchat.api.domain.invite.web.controller

import com.wnl.cashchat.api.common.security.config.SecurityConfig
import com.wnl.cashchat.api.common.security.jwt.JwtTokenHandler
import com.wnl.cashchat.api.domain.invite.exception.AlreadyRedeemedException
import com.wnl.cashchat.api.domain.invite.exception.InvalidCodeException
import com.wnl.cashchat.api.domain.invite.exception.NotEligibleException
import com.wnl.cashchat.api.domain.invite.exception.SelfReferralException
import com.wnl.cashchat.api.domain.invite.persistence.entity.InviteRedemptionStatus
import com.wnl.cashchat.api.domain.invite.service.InviteService
import com.wnl.cashchat.api.domain.invite.service.MyInviteView
import com.wnl.cashchat.api.domain.invite.service.RedeemResult
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(InviteController::class)
@AutoConfigureMockMvc
@Import(SecurityConfig::class)
class InviteControllerTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired private lateinit var mockMvc: MockMvc

    @MockitoBean private lateinit var inviteService: InviteService
    @MockitoBean private lateinit var jwtTokenHandler: JwtTokenHandler
    @MockitoBean private lateinit var jpaMetamodelMappingContext: JpaMetamodelMappingContext

    private fun principal(userId: Long): RequestPostProcessor {
        val auth = UsernamePasswordAuthenticationToken(userId, null, emptyList())
        return SecurityMockMvcRequestPostProcessors.authentication(auth)
    }

    init {
        test("GET /me returns my invite info") {
            whenever(inviteService.getMyInvite(eq(7L), any()))
                .thenReturn(MyInviteView("ABC23X", 3L, true, 500L, 10))

            mockMvc.perform(get("/api/invite/me").with(principal(7L)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.myCode").value("ABC23X"))
                .andExpect(jsonPath("$.invitedCount").value(3))
                .andExpect(jsonPath("$.redeemAvailable").value(true))
                .andExpect(jsonPath("$.rewardCoin").value(500))
                .andExpect(jsonPath("$.rewardEnergy").value(10))
        }

        test("GET /me requires authentication") {
            mockMvc.perform(get("/api/invite/me"))
                .andExpect(status().isUnauthorized)
        }

        test("POST /redeem returns success payload") {
            whenever(inviteService.redeem(eq(7L), eq("XYZ29K"), any()))
                .thenReturn(RedeemResult(10, InviteRedemptionStatus.GRANTED))

            mockMvc.perform(
                post("/api/invite/redeem").with(principal(7L))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"code":"XYZ29K"}""")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.awardedEnergy").value(10))
                .andExpect(jsonPath("$.message").doesNotExist())
        }

        test("POST /redeem maps domain errors to status codes") {
            whenever(inviteService.redeem(eq(1L), any(), any())).thenThrow(AlreadyRedeemedException())
            mockMvc.perform(post("/api/invite/redeem").with(principal(1L))
                .contentType(MediaType.APPLICATION_JSON).content("""{"code":"A"}"""))
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.code").value("ALREADY_REDEEMED"))

            whenever(inviteService.redeem(eq(2L), any(), any())).thenThrow(InvalidCodeException())
            mockMvc.perform(post("/api/invite/redeem").with(principal(2L))
                .contentType(MediaType.APPLICATION_JSON).content("""{"code":"B"}"""))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.code").value("INVALID_CODE"))

            whenever(inviteService.redeem(eq(3L), any(), any())).thenThrow(SelfReferralException())
            mockMvc.perform(post("/api/invite/redeem").with(principal(3L))
                .contentType(MediaType.APPLICATION_JSON).content("""{"code":"C"}"""))
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.code").value("SELF_REFERRAL"))

            whenever(inviteService.redeem(eq(4L), any(), any())).thenThrow(NotEligibleException())
            mockMvc.perform(post("/api/invite/redeem").with(principal(4L))
                .contentType(MediaType.APPLICATION_JSON).content("""{"code":"D"}"""))
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.code").value("NOT_ELIGIBLE"))
        }
    }
}
