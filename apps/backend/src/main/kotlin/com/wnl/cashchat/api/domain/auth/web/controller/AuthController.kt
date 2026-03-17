package com.wnl.cashchat.api.domain.auth.web.controller

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.auth.service.AuthService
import com.wnl.cashchat.api.domain.auth.web.request.TokenRefreshRequest
import com.wnl.cashchat.api.domain.auth.web.response.AuthResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
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

    @GetMapping("/callback/google")
    fun loginWithGoogle(
        @RequestParam code: String,
        @RequestParam(required = false) deviceToken: String?
    ): ResponseEntity<AuthResponse> {
        val response = authService.loginWithOAuth("google-app", AuthProviderType.GOOGLE, code, deviceToken)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/refresh")
    fun reissueToken(@Valid @RequestBody request: TokenRefreshRequest): ResponseEntity<AuthResponse> {
        val response = authService.reissueToken(request.refreshToken)
        return ResponseEntity.ok(response)
    }
}
