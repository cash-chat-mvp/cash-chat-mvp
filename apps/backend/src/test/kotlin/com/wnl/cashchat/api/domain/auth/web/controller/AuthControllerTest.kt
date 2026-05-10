package com.wnl.cashchat.api.domain.auth.web.controller

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.auth.service.AuthService
import com.wnl.cashchat.api.domain.auth.web.response.AuthResponse
import com.wnl.cashchat.api.domain.user.persistence.entity.Role
import org.junit.jupiter.api.Test
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(AuthController::class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var authService: AuthService

    @MockitoBean
    private lateinit var jpaMetamodelMappingContext: JpaMetamodelMappingContext

    @Test
    fun `google callback accepts code and device token from json body`() {
        whenever(
            authService.loginWithOAuth(
                "google-app",
                AuthProviderType.GOOGLE,
                "server-auth-code",
                "device-token"
            )
        ).thenReturn(
            AuthResponse(
                userId = 1L,
                role = Role.MEMBER,
                accessToken = "access-token",
                refreshToken = "refresh-token"
            )
        )

        mockMvc.perform(
            post("/api/auth/callback/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "code": "server-auth-code",
                      "deviceToken": "device-token"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value(1))
            .andExpect(jsonPath("$.role").value("MEMBER"))
            .andExpect(jsonPath("$.accessToken").value("access-token"))
            .andExpect(jsonPath("$.refreshToken").value("refresh-token"))

        verify(authService).loginWithOAuth(
            "google-app",
            AuthProviderType.GOOGLE,
            "server-auth-code",
            "device-token"
        )
    }

    @Test
    fun `google callback accepts nullable device token`() {
        whenever(
            authService.loginWithOAuth(
                "google-app",
                AuthProviderType.GOOGLE,
                "server-auth-code",
                null
            )
        ).thenReturn(
            AuthResponse(
                userId = 1L,
                role = Role.MEMBER,
                accessToken = "access-token",
                refreshToken = "refresh-token"
            )
        )

        mockMvc.perform(
            post("/api/auth/callback/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"code":"server-auth-code"}""")
        )
            .andExpect(status().isOk)

        verify(authService).loginWithOAuth(
            "google-app",
            AuthProviderType.GOOGLE,
            "server-auth-code",
            null
        )
    }

    @Test
    fun `google callback rejects blank code`() {
        mockMvc.perform(
            post("/api/auth/callback/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"code":" ","deviceToken":"device-token"}""")
        )
            .andExpect(status().isBadRequest)

        verifyNoInteractions(authService)
    }

    @Test
    fun `google callback no longer supports get query parameters`() {
        mockMvc.perform(
            get("/api/auth/callback/google")
                .param("code", "server-auth-code")
                .param("deviceToken", "device-token")
        )
            .andExpect(status().isMethodNotAllowed)

        verifyNoInteractions(authService)
    }

    @Test
    fun `logout deletes the submitted refresh token`() {
        mockMvc.perform(
            post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"refresh-token"}""")
        )
            .andExpect(status().isNoContent)

        verify(authService).logout("refresh-token")
    }

    @Test
    fun `logout rejects a blank refresh token`() {
        mockMvc.perform(
            post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":" "}""")
        )
            .andExpect(status().isBadRequest)

        verifyNoInteractions(authService)
    }
}
