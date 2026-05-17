package com.wnl.cashchat.api.domain.auth.web.controller

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.auth.service.AuthService
import com.wnl.cashchat.api.domain.auth.web.request.AppleOAuthCallbackRequest
import com.wnl.cashchat.api.domain.auth.web.request.GoogleOAuthCallbackRequest
import com.wnl.cashchat.api.domain.auth.web.request.LogoutRequest
import com.wnl.cashchat.api.domain.auth.web.request.TokenRefreshRequest
import com.wnl.cashchat.api.domain.auth.web.response.AuthResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService
) {

    @PostMapping("/guest")
    fun loginAsGuest(@RequestParam deviceToken: String): ResponseEntity<AuthResponse> {
        val response = authService.loginAsGuest(deviceToken)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/callback/google")
    fun loginWithGoogle(
        @Valid @RequestBody request: GoogleOAuthCallbackRequest
    ): ResponseEntity<AuthResponse> {
        val response = authService.loginWithOAuth(
            "google-app",
            AuthProviderType.GOOGLE,
            request.code,
            request.deviceToken
        )
        return ResponseEntity.ok(response)
    }

    @PostMapping("/callback/apple")
    fun loginWithApple(
        @Valid @RequestBody request: AppleOAuthCallbackRequest
    ): ResponseEntity<AuthResponse> {
        val response = authService.loginWithApple(
            authorizationCode = request.authorizationCode,
            identityToken = request.identityToken,
            fullName = request.fullName,
            deviceToken = request.deviceToken
        )
        return ResponseEntity.ok(response)
    }

    @PostMapping("/refresh")
    fun reissueToken(@Valid @RequestBody request: TokenRefreshRequest): ResponseEntity<AuthResponse> {
        val response = authService.reissueToken(request.refreshToken)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/logout")
    fun logout(
        authentication: Authentication,
        @Valid @RequestBody request: LogoutRequest
    ): ResponseEntity<Void> {
        val userId = authentication.principal as Long
        authService.logout(userId, request.refreshToken)
        return ResponseEntity.noContent().build()
    }
}
