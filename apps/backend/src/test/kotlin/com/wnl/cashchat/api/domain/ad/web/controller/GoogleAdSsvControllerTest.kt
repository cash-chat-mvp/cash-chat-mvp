package com.wnl.cashchat.api.domain.ad.web.controller

import com.wnl.cashchat.api.common.security.config.SecurityConfig
import com.wnl.cashchat.api.common.security.jwt.JwtTokenHandler
import com.wnl.cashchat.api.domain.ad.exception.GoogleAdSsvTransientException
import com.wnl.cashchat.api.domain.ad.exception.InvalidGoogleAdSsvCallbackException
import com.wnl.cashchat.api.domain.ad.service.GoogleAdSsvService
import com.wnl.cashchat.api.domain.ad.web.exception.GoogleAdSsvExceptionHandler
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(GoogleAdSsvController::class)
@AutoConfigureMockMvc
@Import(SecurityConfig::class, GoogleAdSsvExceptionHandler::class)
class GoogleAdSsvControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var googleAdSsvService: GoogleAdSsvService

    @MockitoBean
    private lateinit var jwtTokenHandler: JwtTokenHandler

    @MockitoBean
    private lateinit var jpaMetamodelMappingContext: JpaMetamodelMappingContext

    @Test
    fun `google ssv callback is public and passes raw query string to service`() {
        val rawQuery = "ad_unit=rewarded-ad-unit&reward_amount=10&reward_item=coin&timestamp=1710000000123" +
            "&transaction_id=txn-123&user_id=user-42&signature=sig&key_id=12345"

        mockMvc.perform(get("/api/ads/google/ssv?$rawQuery"))
            .andExpect(status().isOk)

        verify(googleAdSsvService).verifyAndStore(rawQuery)
    }

    @Test
    fun `google ssv callback maps invalid callback to bad request`() {
        val rawQuery = "ad_unit=rewarded-ad-unit&signature=bad&key_id=12345"
        doThrow(InvalidGoogleAdSsvCallbackException("invalid callback"))
            .`when`(googleAdSsvService)
            .verifyAndStore(rawQuery)

        mockMvc.perform(get("/api/ads/google/ssv?$rawQuery"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_GOOGLE_AD_SSV_CALLBACK"))
            .andExpect(jsonPath("$.message").value("Invalid Google Ad SSV callback."))
    }

    @Test
    fun `google ssv callback maps transient verification failure to service unavailable`() {
        val rawQuery = "ad_unit=rewarded-ad-unit&signature=sig&key_id=12345"
        doThrow(GoogleAdSsvTransientException("public keys unavailable"))
            .`when`(googleAdSsvService)
            .verifyAndStore(rawQuery)

        mockMvc.perform(get("/api/ads/google/ssv?$rawQuery"))
            .andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.code").value("GOOGLE_AD_SSV_TEMPORARILY_UNAVAILABLE"))
    }

    @Test
    fun `other ad endpoints still require authentication`() {
        mockMvc.perform(get("/api/ads/private"))
            .andExpect(status().isUnauthorized)

        verifyNoInteractions(googleAdSsvService)
    }
}
