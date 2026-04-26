package com.wnl.cashchat.api.domain.auth.service

import com.wnl.cashchat.api.common.security.jwt.JwtTokenHandler
import com.wnl.cashchat.api.domain.auth.oauth.properties.OAuthProperties
import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.auth.persistence.repository.RefreshTokenRepository
import com.wnl.cashchat.api.domain.point.service.UserPointService
import com.wnl.cashchat.api.domain.user.persistence.entity.Role
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import com.wnl.cashchat.api.domain.user.persistence.repository.UserRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.client.RestClient

class AuthServiceTest : FunSpec({
    test("loginAsGuest initializes points for a newly created guest user") {
        val userRepository = mock<UserRepository>()
        val refreshTokenRepository = mock<RefreshTokenRepository>()
        val jwtTokenHandler = mock<JwtTokenHandler>()
        val userPointService = mock<UserPointService>()
        val authService = AuthService(
            userRepository = userRepository,
            refreshTokenRepository = refreshTokenRepository,
            jwtTokenHandler = jwtTokenHandler,
            oAuthProperties = OAuthProperties(),
            restClient = mock<RestClient>(),
            userPointService = userPointService,
            oAuthUserInfoExtractors = emptyList(),
        )

        val guest = User(
            id = 1L,
            role = Role.GUEST,
            deviceToken = "device-1",
            provider = AuthProviderType.NONE,
            name = "Guest",
        )
        whenever(userRepository.findByDeviceToken("device-1")).thenReturn(null)
        whenever(userRepository.save(argThat<User> { deviceToken == "device-1" })).thenReturn(guest)
        whenever(jwtTokenHandler.createAccessToken(1L, Role.GUEST)).thenReturn("access-token")

        val response = authService.loginAsGuest("device-1")

        response.accessToken shouldBe "access-token"
        verify(userPointService).ensureInitialized(
            argThat<User> {
                id == 1L && deviceToken == "device-1"
            }
        )
    }
})
