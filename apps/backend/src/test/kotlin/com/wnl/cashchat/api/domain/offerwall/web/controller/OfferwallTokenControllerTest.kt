package com.wnl.cashchat.api.domain.offerwall.web.controller

import com.wnl.cashchat.api.common.security.config.SecurityConfig
import com.wnl.cashchat.api.common.security.jwt.JwtTokenHandler
import com.wnl.cashchat.api.domain.offerwall.service.OfferwallUserTokenService
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(OfferwallController::class)
@AutoConfigureMockMvc
@Import(SecurityConfig::class)
class OfferwallTokenControllerTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired private lateinit var mockMvc: MockMvc

    @MockitoBean private lateinit var offerwallUserTokenService: OfferwallUserTokenService
    @MockitoBean private lateinit var jwtTokenHandler: JwtTokenHandler
    @MockitoBean private lateinit var jpaMetamodelMappingContext: JpaMetamodelMappingContext

    // 인증 principal 을 Long userId 로 주입 (컨트롤러가 principal as? Long 으로 읽음)
    private fun principal(userId: Long): RequestPostProcessor {
        val auth = org.springframework.security.authentication.UsernamePasswordAuthenticationToken(userId, null, emptyList())
        return SecurityMockMvcRequestPostProcessors.authentication(auth)
    }

    init {
        test("issue user-token returns token for authenticated user") {
            whenever(offerwallUserTokenService.tokenFor(42L)).thenReturn("tok-42")

            mockMvc.perform(post("/api/offerwall/tnk/user-token").with(principal(42L)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.token").value("tok-42"))
        }

        test("user-token requires authentication") {
            mockMvc.perform(post("/api/offerwall/tnk/user-token"))
                .andExpect(status().isUnauthorized)
        }
    }
}
