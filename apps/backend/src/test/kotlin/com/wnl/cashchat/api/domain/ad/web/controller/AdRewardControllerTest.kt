package com.wnl.cashchat.api.domain.ad.web.controller

import com.wnl.cashchat.api.common.security.jwt.JwtTokenHandler
import com.wnl.cashchat.api.domain.ad.persistence.entity.AdRewardNonce
import com.wnl.cashchat.api.domain.ad.service.AdRewardNonceService
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

@WebMvcTest(AdRewardController::class)
@AutoConfigureMockMvc(addFilters = false)
class AdRewardControllerTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var mockMvc: MockMvc

    @MockBean lateinit var adRewardNonceService: AdRewardNonceService
    @MockBean lateinit var jwtTokenHandler: JwtTokenHandler
    @MockBean(name = "jpaMappingContext") lateinit var jpaMappingContext: JpaMetamodelMappingContext

    private val principal = UsernamePasswordAuthenticationToken(1L, null)

    init {
        test("issue-nonce returns nonce and expiry for the authenticated user") {
            val expiresAt = Instant.parse("2026-05-31T00:10:00Z")
            whenever(adRewardNonceService.issueFor(eq(1L), any())).thenReturn(
                AdRewardNonce(nonce = "abc123", userId = 1L, expiresAt = expiresAt)
            )

            mockMvc.perform(post("/api/ads/reward/issue-nonce").principal(principal))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.nonce").value("abc123"))
                .andExpect(jsonPath("$.expiresAt").exists())
        }
    }
}
