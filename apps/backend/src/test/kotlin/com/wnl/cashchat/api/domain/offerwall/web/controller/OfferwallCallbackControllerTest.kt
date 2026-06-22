package com.wnl.cashchat.api.domain.offerwall.web.controller

import com.wnl.cashchat.api.common.security.config.SecurityConfig
import com.wnl.cashchat.api.common.security.jwt.JwtTokenHandler
import com.wnl.cashchat.api.domain.offerwall.persistence.entity.OfferwallPlatform
import com.wnl.cashchat.api.domain.offerwall.persistence.entity.TnkOfferwallStatus
import com.wnl.cashchat.api.domain.offerwall.properties.TnkOfferwallProperties
import com.wnl.cashchat.api.domain.offerwall.service.OfferwallUserTokenService
import com.wnl.cashchat.api.domain.offerwall.service.TnkOfferwallCallbackParams
import com.wnl.cashchat.api.domain.offerwall.service.TnkOfferwallService
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(OfferwallController::class)
@AutoConfigureMockMvc
@Import(SecurityConfig::class)
class OfferwallCallbackControllerTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired private lateinit var mockMvc: MockMvc

    @MockitoBean private lateinit var offerwallUserTokenService: OfferwallUserTokenService
    @MockitoBean private lateinit var tnkOfferwallService: TnkOfferwallService
    @MockitoBean private lateinit var tnkOfferwallProperties: TnkOfferwallProperties
    @MockitoBean private lateinit var jwtTokenHandler: JwtTokenHandler
    @MockitoBean private lateinit var jpaMetamodelMappingContext: JpaMetamodelMappingContext

    init {
        beforeTest {
            whenever(tnkOfferwallProperties.ack).thenReturn(TnkOfferwallProperties.Ack())
        }

        test("android callback is public, passes platform and params to service, returns SUCCESS ack") {
            whenever(tnkOfferwallService.handleCallback(any(), any(), any())).thenReturn(TnkOfferwallStatus.GRANTED)

            mockMvc.perform(
                post("/api/offerwall/tnk/callback/android")
                    .param("seq_id", "seq-1")
                    .param("pay_pnt", "1500")
                    .param("md_user_nm", "tok-1")
                    .param("md_chk", "hash-1")
            )
                .andExpect(status().isOk)
                .andExpect(content().string("SUCCESS"))

            verify(tnkOfferwallService).handleCallback(
                eq(OfferwallPlatform.ANDROID),
                argThat<TnkOfferwallCallbackParams> {
                    seqId == "seq-1" && payPnt == 1500L && mdUserNm == "tok-1" && mdChk == "hash-1"
                },
                any(),
            )
        }

        test("ios callback routes to IOS platform") {
            whenever(tnkOfferwallService.handleCallback(any(), any(), any())).thenReturn(TnkOfferwallStatus.GRANTED)

            mockMvc.perform(
                post("/api/offerwall/tnk/callback/ios")
                    .param("seq_id", "seq-9")
                    .param("pay_pnt", "1000")
                    .param("md_user_nm", "tok-9")
                    .param("md_chk", "hash-9")
            )
                .andExpect(status().isOk)

            verify(tnkOfferwallService).handleCallback(eq(OfferwallPlatform.IOS), any(), any())
        }

        test("callback returns SUCCESS ack even when rejected (no retry storm)") {
            whenever(tnkOfferwallService.handleCallback(any(), any(), any()))
                .thenReturn(TnkOfferwallStatus.REJECTED_BAD_SIGNATURE)

            mockMvc.perform(
                post("/api/offerwall/tnk/callback/android")
                    .param("seq_id", "seq-2")
                    .param("pay_pnt", "1000")
                    .param("md_user_nm", "tok-2")
                    .param("md_chk", "bad")
            )
                .andExpect(status().isOk)
                .andExpect(content().string("SUCCESS"))
        }

        test("unknown platform path returns 400") {
            mockMvc.perform(
                post("/api/offerwall/tnk/callback/web")
                    .param("seq_id", "seq-3")
                    .param("pay_pnt", "1000")
                    .param("md_user_nm", "tok-3")
                    .param("md_chk", "x")
            )
                .andExpect(status().isBadRequest)
        }
    }
}
