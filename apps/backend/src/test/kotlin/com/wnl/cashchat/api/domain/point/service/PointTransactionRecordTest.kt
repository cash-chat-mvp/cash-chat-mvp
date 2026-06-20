package com.wnl.cashchat.api.domain.point.service

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.point.exception.InsufficientPointsException
import com.wnl.cashchat.api.domain.point.persistence.entity.PointTransaction
import com.wnl.cashchat.api.domain.point.persistence.entity.PointTransactionReason
import com.wnl.cashchat.api.domain.point.persistence.entity.UserPoint
import com.wnl.cashchat.api.domain.point.persistence.repository.PointTransactionRepository
import com.wnl.cashchat.api.domain.point.persistence.repository.UserPointRepository
import com.wnl.cashchat.api.domain.point.properties.PointProperties
import com.wnl.cashchat.api.domain.user.persistence.entity.Role
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PointTransactionRecordTest : FunSpec({
    lateinit var userPointRepository: UserPointRepository
    lateinit var pointTransactionRepository: PointTransactionRepository
    lateinit var userPointService: UserPointService

    val user = User(id = 1L, role = Role.MEMBER, provider = AuthProviderType.NONE, name = "tester")

    beforeTest {
        userPointRepository = mock()
        pointTransactionRepository = mock()
        userPointService = UserPointService(
            userPointRepository = userPointRepository,
            pointTransactionRepository = pointTransactionRepository,
            pointProperties = PointProperties(initialBalance = 1L),
        )
    }

    test("records a positive accrual, charges balance, and persists a ledger row") {
        val userPoint = UserPoint(user = user, balance = 100L)
        whenever(userPointRepository.findByUserIdForUpdate(1L)).thenReturn(userPoint)
        whenever(pointTransactionRepository.findByIdempotencyKey("k1")).thenReturn(null)
        whenever(pointTransactionRepository.save(any<PointTransaction>())).thenAnswer { it.arguments[0] }

        val result = userPointService.recordTransaction(
            userId = 1L, delta = 20L, reason = PointTransactionReason.ATTENDANCE, idempotencyKey = "k1",
        )

        userPoint.balance shouldBe 120L
        result.balanceAfter shouldBe 120L
        result.delta shouldBe 20L
        result.idempotencyKey shouldBe "k1"
        verify(pointTransactionRepository).save(any<PointTransaction>())
    }

    test("returns the existing ledger row on duplicate idempotency key without re-charging") {
        val userPoint = UserPoint(user = user, balance = 100L)
        val existing = PointTransaction(
            userId = 1L, delta = 20L, balanceAfter = 120L,
            reason = PointTransactionReason.ATTENDANCE, idempotencyKey = "k1",
        )
        whenever(userPointRepository.findByUserIdForUpdate(1L)).thenReturn(userPoint)
        whenever(pointTransactionRepository.findByIdempotencyKey("k1")).thenReturn(existing)

        val result = userPointService.recordTransaction(
            userId = 1L, delta = 20L, reason = PointTransactionReason.ATTENDANCE, idempotencyKey = "k1",
        )

        result shouldBe existing
        userPoint.balance shouldBe 100L
        verify(pointTransactionRepository, never()).save(any<PointTransaction>())
    }

    test("deducts balance for a negative delta when sufficient") {
        val userPoint = UserPoint(user = user, balance = 100L)
        whenever(userPointRepository.findByUserIdForUpdate(1L)).thenReturn(userPoint)
        whenever(pointTransactionRepository.findByIdempotencyKey("k2")).thenReturn(null)
        whenever(pointTransactionRepository.save(any<PointTransaction>())).thenAnswer { it.arguments[0] }

        userPointService.recordTransaction(
            userId = 1L, delta = -30L, reason = PointTransactionReason.AD_REWARD, idempotencyKey = "k2",
        )

        userPoint.balance shouldBe 70L
    }

    test("throws InsufficientPointsException for a negative delta exceeding balance") {
        val userPoint = UserPoint(user = user, balance = 10L)
        whenever(userPointRepository.findByUserIdForUpdate(1L)).thenReturn(userPoint)
        whenever(pointTransactionRepository.findByIdempotencyKey("k3")).thenReturn(null)

        shouldThrow<InsufficientPointsException> {
            userPointService.recordTransaction(
                userId = 1L, delta = -30L, reason = PointTransactionReason.AD_REWARD, idempotencyKey = "k3",
            )
        }
        userPoint.balance shouldBe 10L
        verify(pointTransactionRepository, never()).save(any<PointTransaction>())
    }

    test("throws when the user point row is missing") {
        whenever(userPointRepository.findByUserIdForUpdate(1L)).thenReturn(null)

        shouldThrow<IllegalStateException> {
            userPointService.recordTransaction(
                userId = 1L, delta = 20L, reason = PointTransactionReason.ATTENDANCE, idempotencyKey = "k4",
            )
        }
    }
})
