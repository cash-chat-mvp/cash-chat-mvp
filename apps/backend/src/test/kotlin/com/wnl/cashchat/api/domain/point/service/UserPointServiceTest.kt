package com.wnl.cashchat.api.domain.point.service

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.point.persistence.entity.UserPoint
import com.wnl.cashchat.api.domain.point.persistence.repository.UserPointRepository
import com.wnl.cashchat.api.domain.point.properties.PointProperties
import com.wnl.cashchat.api.domain.user.persistence.entity.Role
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class UserPointServiceTest : FunSpec({
    lateinit var userPointRepository: UserPointRepository
    lateinit var userPointService: UserPointService

    beforeTest {
        userPointRepository = mock()
        userPointService = UserPointService(
            userPointRepository = userPointRepository,
            pointProperties = PointProperties(initialBalance = 3L),
        )
    }

    test("hasEnoughBalance treats a missing point row as zero balance") {
        whenever(userPointRepository.existsByUserIdAndBalanceGreaterThanEqual(1L, 1L)).thenReturn(false)

        userPointService.hasEnoughBalance(1L) shouldBe false
    }

    test("hasEnoughBalance returns false when balance is zero") {
        whenever(userPointRepository.existsByUserIdAndBalanceGreaterThanEqual(1L, 1L)).thenReturn(false)

        userPointService.hasEnoughBalance(1L) shouldBe false
    }

    test("hasEnoughBalance returns true when balance is at least one") {
        whenever(userPointRepository.existsByUserIdAndBalanceGreaterThanEqual(1L, 1L)).thenReturn(true)

        userPointService.hasEnoughBalance(1L) shouldBe true
    }

    test("ensureInitialized creates a point row with configured initial balance") {
        val user = User(id = 1L, role = Role.GUEST, provider = AuthProviderType.NONE, name = "Guest")
        val saved = UserPoint(user = user, balance = 3L)

        whenever(userPointRepository.findByUserId(1L)).thenReturn(null)
        whenever(userPointRepository.save(any<UserPoint>())).thenReturn(saved)

        userPointService.ensureInitialized(user) shouldBe saved

        verify(userPointRepository).save(
            argThat<UserPoint> {
                this.user.id == 1L && this.balance == 3L
            }
        )
    }

    test("PointProperties defaults initial balance to one point") {
        PointProperties().initialBalance shouldBe 1L
    }
})
