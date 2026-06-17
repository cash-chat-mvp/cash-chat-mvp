package com.wnl.cashchat.api.domain.auth.service

import com.wnl.cashchat.api.common.security.jwt.JwtTokenHandler
import com.wnl.cashchat.api.domain.auth.exception.InvalidTokenException
import com.wnl.cashchat.api.domain.auth.exception.OAuthException
import com.wnl.cashchat.api.domain.auth.oauth.apple.AppleIdTokenClaims
import com.wnl.cashchat.api.domain.auth.oauth.apple.AppleIdTokenValidator
import com.wnl.cashchat.api.domain.auth.oauth.apple.AppleTokenClient
import com.wnl.cashchat.api.domain.auth.oauth.apple.AppleTokenResponse
import com.wnl.cashchat.api.domain.auth.oauth.apple.AppleUserInfoExtractor
import com.wnl.cashchat.api.domain.auth.oauth.model.OAuthUserInfo
import com.wnl.cashchat.api.domain.auth.oauth.properties.OAuthProperties
import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.auth.persistence.repository.RefreshTokenRepository
import com.wnl.cashchat.api.domain.energy.service.EnergyService
import com.wnl.cashchat.api.domain.evolution.service.EvolutionService
import com.wnl.cashchat.api.domain.point.service.UserPointService
import com.wnl.cashchat.api.domain.user.persistence.entity.Role
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import com.wnl.cashchat.api.domain.user.persistence.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.web.client.RestClient

class AuthServiceTest : FunSpec({
    test("loginAsGuest initializes points for a newly created guest user") {
        val userRepository = mock<UserRepository>()
        val refreshTokenRepository = mock<RefreshTokenRepository>()
        val jwtTokenHandler = mock<JwtTokenHandler>()
        val userPointService = mock<UserPointService>()
        val evolutionService = mock<EvolutionService>()
        val energyService = mock<EnergyService>()
        val authService = AuthService(
            userRepository = userRepository,
            refreshTokenRepository = refreshTokenRepository,
            jwtTokenHandler = jwtTokenHandler,
            transactionManager = NoOpTransactionManager(),
            oAuthProperties = OAuthProperties(),
            restClient = mock<RestClient>(),
            userPointService = userPointService,
            evolutionService = evolutionService,
            energyService = energyService,
            appleTokenClient = mock<AppleTokenClient>(),
            appleIdTokenValidator = mock<AppleIdTokenValidator>(),
            appleUserInfoExtractor = mock<AppleUserInfoExtractor>(),
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
        verify(evolutionService).ensureInitialized(
            argThat<User> {
                id == 1L && deviceToken == "device-1"
            }
        )
        verify(energyService).ensureInitialized(
            argThat<User> {
                id == 1L && deviceToken == "device-1"
            }
        )
    }

    test("logout deletes the submitted refresh token for the caller") {
        val userRepository = mock<UserRepository>()
        val refreshTokenRepository = mock<RefreshTokenRepository>()
        val jwtTokenHandler = mock<JwtTokenHandler>()
        val userPointService = mock<UserPointService>()
        val authService = AuthService(
            userRepository = userRepository,
            refreshTokenRepository = refreshTokenRepository,
            jwtTokenHandler = jwtTokenHandler,
            transactionManager = NoOpTransactionManager(),
            oAuthProperties = OAuthProperties(),
            restClient = mock<RestClient>(),
            userPointService = userPointService,
            evolutionService = mock<EvolutionService>(),
            energyService = mock<EnergyService>(),
            appleTokenClient = mock<AppleTokenClient>(),
            appleIdTokenValidator = mock<AppleIdTokenValidator>(),
            appleUserInfoExtractor = mock<AppleUserInfoExtractor>(),
            oAuthUserInfoExtractors = emptyList(),
        )
        whenever(refreshTokenRepository.deleteByUserIdAndTokenReturningCount(1L, "refresh-token")).thenReturn(1)

        authService.logout(1L, "refresh-token")

        verify(refreshTokenRepository).deleteByUserIdAndTokenReturningCount(1L, "refresh-token")
    }

    test("logout succeeds when the refresh token is already absent") {
        val userRepository = mock<UserRepository>()
        val refreshTokenRepository = mock<RefreshTokenRepository>()
        val jwtTokenHandler = mock<JwtTokenHandler>()
        val userPointService = mock<UserPointService>()
        val authService = AuthService(
            userRepository = userRepository,
            refreshTokenRepository = refreshTokenRepository,
            jwtTokenHandler = jwtTokenHandler,
            transactionManager = NoOpTransactionManager(),
            oAuthProperties = OAuthProperties(),
            restClient = mock<RestClient>(),
            userPointService = userPointService,
            evolutionService = mock<EvolutionService>(),
            energyService = mock<EnergyService>(),
            appleTokenClient = mock<AppleTokenClient>(),
            appleIdTokenValidator = mock<AppleIdTokenValidator>(),
            appleUserInfoExtractor = mock<AppleUserInfoExtractor>(),
            oAuthUserInfoExtractors = emptyList(),
        )
        whenever(refreshTokenRepository.deleteByUserIdAndTokenReturningCount(1L, "missing-token")).thenReturn(0)

        authService.logout(1L, "missing-token")

        verify(refreshTokenRepository).deleteByUserIdAndTokenReturningCount(1L, "missing-token")
    }

    test("logout calls delete with caller userId and token when token belongs to another user") {
        val userRepository = mock<UserRepository>()
        val refreshTokenRepository = mock<RefreshTokenRepository>()
        val jwtTokenHandler = mock<JwtTokenHandler>()
        val userPointService = mock<UserPointService>()
        val authService = AuthService(
            userRepository = userRepository,
            refreshTokenRepository = refreshTokenRepository,
            jwtTokenHandler = jwtTokenHandler,
            transactionManager = NoOpTransactionManager(),
            oAuthProperties = OAuthProperties(),
            restClient = mock<RestClient>(),
            userPointService = userPointService,
            evolutionService = mock<EvolutionService>(),
            energyService = mock<EnergyService>(),
            appleTokenClient = mock<AppleTokenClient>(),
            appleIdTokenValidator = mock<AppleIdTokenValidator>(),
            appleUserInfoExtractor = mock<AppleUserInfoExtractor>(),
            oAuthUserInfoExtractors = emptyList(),
        )
        whenever(refreshTokenRepository.deleteByUserIdAndTokenReturningCount(2L, "refresh-token")).thenReturn(0)

        authService.logout(2L, "refresh-token")

        verify(refreshTokenRepository).deleteByUserIdAndTokenReturningCount(2L, "refresh-token")
    }

    test("loginWithApple upgrades matching guest user to member and clears device token") {
        val userRepository = mock<UserRepository>()
        val refreshTokenRepository = mock<RefreshTokenRepository>()
        val jwtTokenHandler = mock<JwtTokenHandler>()
        val userPointService = mock<UserPointService>()
        val evolutionService = mock<EvolutionService>()
        val energyService = mock<EnergyService>()
        val appleTokenClient = mock<AppleTokenClient>()
        val appleIdTokenValidator = mock<AppleIdTokenValidator>()
        val appleUserInfoExtractor = mock<AppleUserInfoExtractor>()
        val authService = AuthService(
            userRepository = userRepository,
            refreshTokenRepository = refreshTokenRepository,
            jwtTokenHandler = jwtTokenHandler,
            transactionManager = NoOpTransactionManager(),
            oAuthProperties = OAuthProperties(),
            restClient = mock<RestClient>(),
            userPointService = userPointService,
            evolutionService = evolutionService,
            energyService = energyService,
            appleTokenClient = appleTokenClient,
            appleIdTokenValidator = appleIdTokenValidator,
            appleUserInfoExtractor = appleUserInfoExtractor,
            oAuthUserInfoExtractors = emptyList(),
        )
        val guest = User(
            id = 1L,
            role = Role.GUEST,
            deviceToken = "device-1",
            provider = AuthProviderType.NONE,
            name = "Guest",
        )
        val appleInfo = OAuthUserInfo(
            providerId = "apple-subject",
            email = "apple@example.com",
            name = "Apple Name",
            profileImageUrl = null,
        )
        whenever(appleTokenClient.exchangeAuthorizationCode("authorization-code"))
            .thenReturn(AppleTokenResponse(idToken = "apple-id-token"))
        whenever(appleIdTokenValidator.validate("apple-id-token"))
            .thenReturn(AppleIdTokenClaims("apple-subject", "apple@example.com", true))
        whenever(appleUserInfoExtractor.extract(AppleIdTokenClaims("apple-subject", "apple@example.com", true), "Apple Name"))
            .thenReturn(appleInfo)
        whenever(userRepository.findByProviderAndProviderId(AuthProviderType.APPLE, "apple-subject")).thenReturn(null)
        whenever(userRepository.findByDeviceToken("device-1")).thenReturn(guest)
        whenever(userRepository.save(argThat<User> { provider == AuthProviderType.APPLE })).thenAnswer { it.arguments[0] as User }
        whenever(jwtTokenHandler.createAccessToken(1L, Role.MEMBER)).thenReturn("access-token")

        val response = authService.loginWithApple(
            authorizationCode = "authorization-code",
            identityToken = null,
            fullName = "Apple Name",
            deviceToken = "device-1",
        )

        guest.provider shouldBe AuthProviderType.APPLE
        guest.providerId shouldBe "apple-subject"
        guest.email shouldBe "apple@example.com"
        guest.name shouldBe "Apple Name"
        guest.role shouldBe Role.MEMBER
        guest.deviceToken shouldBe null
        response.accessToken shouldBe "access-token"
        response.userId shouldBe 1L
        response.role shouldBe Role.MEMBER
        verify(userPointService).ensureInitialized(guest)
        verify(evolutionService).ensureInitialized(guest)
        verify(energyService).ensureInitialized(guest)
    }

    test("loginWithApple returns existing Apple user without creating duplicate") {
        val userRepository = mock<UserRepository>()
        val refreshTokenRepository = mock<RefreshTokenRepository>()
        val jwtTokenHandler = mock<JwtTokenHandler>()
        val userPointService = mock<UserPointService>()
        val evolutionService = mock<EvolutionService>()
        val energyService = mock<EnergyService>()
        val appleTokenClient = mock<AppleTokenClient>()
        val appleIdTokenValidator = mock<AppleIdTokenValidator>()
        val appleUserInfoExtractor = mock<AppleUserInfoExtractor>()
        val authService = AuthService(
            userRepository = userRepository,
            refreshTokenRepository = refreshTokenRepository,
            jwtTokenHandler = jwtTokenHandler,
            transactionManager = NoOpTransactionManager(),
            oAuthProperties = OAuthProperties(),
            restClient = mock<RestClient>(),
            userPointService = userPointService,
            evolutionService = evolutionService,
            energyService = energyService,
            appleTokenClient = appleTokenClient,
            appleIdTokenValidator = appleIdTokenValidator,
            appleUserInfoExtractor = appleUserInfoExtractor,
            oAuthUserInfoExtractors = emptyList(),
        )
        val existingUser = User(
            id = 2L,
            role = Role.MEMBER,
            provider = AuthProviderType.APPLE,
            providerId = "apple-subject",
            email = "stored@example.com",
            name = "Stored Name",
        )
        val appleInfo = OAuthUserInfo(
            providerId = "apple-subject",
            email = null,
            name = "Apple User",
            profileImageUrl = null,
        )
        whenever(appleTokenClient.exchangeAuthorizationCode("authorization-code"))
            .thenReturn(AppleTokenResponse(idToken = "apple-id-token"))
        whenever(appleIdTokenValidator.validate("apple-id-token"))
            .thenReturn(AppleIdTokenClaims("apple-subject", null, null))
        whenever(appleUserInfoExtractor.extract(AppleIdTokenClaims("apple-subject", null, null), null))
            .thenReturn(appleInfo)
        whenever(userRepository.findByProviderAndProviderId(AuthProviderType.APPLE, "apple-subject")).thenReturn(existingUser)
        whenever(jwtTokenHandler.createAccessToken(2L, Role.MEMBER)).thenReturn("access-token")

        val response = authService.loginWithApple(
            authorizationCode = "authorization-code",
            identityToken = null,
            fullName = null,
            deviceToken = null,
        )

        existingUser.email shouldBe "stored@example.com"
        existingUser.name shouldBe "Stored Name"
        response.accessToken shouldBe "access-token"
        response.userId shouldBe 2L
        response.role shouldBe Role.MEMBER
        verify(userPointService).ensureInitialized(existingUser)
        verify(evolutionService).ensureInitialized(existingUser)
        verify(energyService).ensureInitialized(existingUser)
    }

    test("loginWithApple rejects missing Apple id token before creating or upgrading user") {
        val userRepository = mock<UserRepository>()
        val refreshTokenRepository = mock<RefreshTokenRepository>()
        val jwtTokenHandler = mock<JwtTokenHandler>()
        val userPointService = mock<UserPointService>()
        val evolutionService = mock<EvolutionService>()
        val energyService = mock<EnergyService>()
        val appleTokenClient = mock<AppleTokenClient>()
        val appleIdTokenValidator = mock<AppleIdTokenValidator>()
        val appleUserInfoExtractor = mock<AppleUserInfoExtractor>()
        val authService = AuthService(
            userRepository = userRepository,
            refreshTokenRepository = refreshTokenRepository,
            jwtTokenHandler = jwtTokenHandler,
            transactionManager = NoOpTransactionManager(),
            oAuthProperties = OAuthProperties(),
            restClient = mock<RestClient>(),
            userPointService = userPointService,
            evolutionService = evolutionService,
            energyService = energyService,
            appleTokenClient = appleTokenClient,
            appleIdTokenValidator = appleIdTokenValidator,
            appleUserInfoExtractor = appleUserInfoExtractor,
            oAuthUserInfoExtractors = emptyList(),
        )
        whenever(appleTokenClient.exchangeAuthorizationCode("authorization-code"))
            .thenReturn(AppleTokenResponse(idToken = null))

        shouldThrow<OAuthException> {
            authService.loginWithApple(
                authorizationCode = "authorization-code",
                identityToken = null,
                fullName = null,
                deviceToken = null,
            )
        }.message shouldBe "Missing id_token in Apple response"

        verify(userRepository, never()).save(any())
        verify(userPointService, never()).ensureInitialized(any())
        verify(evolutionService, never()).ensureInitialized(any())
        verify(energyService, never()).ensureInitialized(any())
    }

    test("reissueToken fails fast with InvalidTokenException for an unknown refresh token") {
        // 회귀 가드: refresh 엔드포인트는 알 수 없는 토큰에 대해 (hang 이 아니라) 즉시 예외로 끝나야 한다.
        // 풀 고갈 시 이 경로가 무응답이 되던 장애의 victim 이었음. 로직 자체는 빠른 실패가 정상.
        val refreshTokenRepository = mock<RefreshTokenRepository>()
        val authService = AuthService(
            userRepository = mock<UserRepository>(),
            refreshTokenRepository = refreshTokenRepository,
            jwtTokenHandler = mock<JwtTokenHandler>(),
            transactionManager = NoOpTransactionManager(),
            oAuthProperties = OAuthProperties(),
            restClient = mock<RestClient>(),
            userPointService = mock<UserPointService>(),
            evolutionService = mock<EvolutionService>(),
            energyService = mock<EnergyService>(),
            appleTokenClient = mock<AppleTokenClient>(),
            appleIdTokenValidator = mock<AppleIdTokenValidator>(),
            appleUserInfoExtractor = mock<AppleUserInfoExtractor>(),
            oAuthUserInfoExtractors = emptyList(),
        )
        whenever(refreshTokenRepository.findByTokenForUpdate("dummy-token")).thenReturn(null)

        shouldThrow<InvalidTokenException> {
            authService.reissueToken("dummy-token")
        }
    }

    test("loginWithApple performs external Apple calls before opening a DB transaction") {
        // 구조 가드: 외부 IdP 호출(토큰 교환 + id_token 검증/JWKS)은 트랜잭션 시작 전에 수행되어야 한다.
        // 그렇지 않으면 외부 호출 대기 동안 DB 커넥션이 점유되어 풀 고갈을 유발한다.
        val userRepository = mock<UserRepository>()
        val jwtTokenHandler = mock<JwtTokenHandler>()
        val appleTokenClient = mock<AppleTokenClient>()
        val appleIdTokenValidator = mock<AppleIdTokenValidator>()
        val appleUserInfoExtractor = mock<AppleUserInfoExtractor>()
        val transactionManager = mock<PlatformTransactionManager>()
        whenever(transactionManager.getTransaction(any())).thenReturn(SimpleTransactionStatus())
        whenever(jwtTokenHandler.createAccessToken(any(), any())).thenReturn("access-token")

        val authService = AuthService(
            userRepository = userRepository,
            refreshTokenRepository = mock<RefreshTokenRepository>(),
            jwtTokenHandler = jwtTokenHandler,
            transactionManager = transactionManager,
            oAuthProperties = OAuthProperties(),
            restClient = mock<RestClient>(),
            userPointService = mock<UserPointService>(),
            evolutionService = mock<EvolutionService>(),
            energyService = mock<EnergyService>(),
            appleTokenClient = appleTokenClient,
            appleIdTokenValidator = appleIdTokenValidator,
            appleUserInfoExtractor = appleUserInfoExtractor,
            oAuthUserInfoExtractors = emptyList(),
        )
        val existingUser = User(
            id = 7L,
            role = Role.MEMBER,
            provider = AuthProviderType.APPLE,
            providerId = "apple-subject",
            name = "Stored",
        )
        val appleInfo = OAuthUserInfo(
            providerId = "apple-subject",
            email = null,
            name = "Apple User",
            profileImageUrl = null,
        )
        whenever(appleTokenClient.exchangeAuthorizationCode("authorization-code"))
            .thenReturn(AppleTokenResponse(idToken = "apple-id-token"))
        whenever(appleIdTokenValidator.validate("apple-id-token"))
            .thenReturn(AppleIdTokenClaims("apple-subject", null, null))
        whenever(appleUserInfoExtractor.extract(AppleIdTokenClaims("apple-subject", null, null), null))
            .thenReturn(appleInfo)
        whenever(userRepository.findByProviderAndProviderId(AuthProviderType.APPLE, "apple-subject"))
            .thenReturn(existingUser)

        authService.loginWithApple(
            authorizationCode = "authorization-code",
            identityToken = null,
            fullName = null,
            deviceToken = null,
        )

        // 외부 Apple 토큰 교환이 트랜잭션 시작(getTransaction)보다 먼저 호출되었는지 순서 검증
        inOrder(appleTokenClient, transactionManager) {
            verify(appleTokenClient).exchangeAuthorizationCode("authorization-code")
            verify(transactionManager).getTransaction(any())
        }
    }
}) {
    private class NoOpTransactionManager : PlatformTransactionManager {
        override fun getTransaction(definition: TransactionDefinition?): TransactionStatus = SimpleTransactionStatus()

        override fun commit(status: TransactionStatus) = Unit

        override fun rollback(status: TransactionStatus) = Unit
    }
}
