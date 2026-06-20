package com.wnl.cashchat.api.domain.point.service

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.point.persistence.entity.PointTransaction
import com.wnl.cashchat.api.domain.point.persistence.entity.PointTransactionReason
import com.wnl.cashchat.api.domain.point.persistence.entity.UserPoint
import com.wnl.cashchat.api.domain.point.persistence.repository.PointTransactionRepository
import com.wnl.cashchat.api.domain.point.persistence.repository.UserPointRepository
import com.wnl.cashchat.api.domain.point.properties.PointProperties
import com.wnl.cashchat.api.domain.user.persistence.entity.Role
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import jakarta.validation.Validation
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort

class UserPointServiceTest : FunSpec({
    lateinit var userPointRepository: UserPointRepository
    lateinit var pointTransactionRepository: PointTransactionRepository
    lateinit var userPointService: UserPointService

    beforeTest {
        userPointRepository = mock()
        pointTransactionRepository = mock()
        userPointService = UserPointService(
            userPointRepository = userPointRepository,
            pointTransactionRepository = pointTransactionRepository,
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
        whenever(userPointRepository.saveAndFlush(any<UserPoint>())).thenReturn(saved)

        userPointService.ensureInitialized(user) shouldBe saved

        verify(userPointRepository).saveAndFlush(
            argThat<UserPoint> {
                this.user.id == 1L && this.balance == 3L
            }
        )
    }

    test("ensureInitialized recovers when another request creates the point row concurrently") {
        val user = User(id = 1L, role = Role.GUEST, provider = AuthProviderType.NONE, name = "Guest")
        val savedByConcurrentRequest = UserPoint(user = user, balance = 3L)

        whenever(userPointRepository.findByUserId(1L)).thenReturn(null, savedByConcurrentRequest)
        whenever(userPointRepository.saveAndFlush(any<UserPoint>()))
            .thenThrow(DataIntegrityViolationException("duplicate user point"))

        userPointService.ensureInitialized(user) shouldBe savedByConcurrentRequest
    }

    test("PointProperties defaults initial balance to one point") {
        PointProperties().initialBalance shouldBe 1L
    }

    test("PointProperties rejects zero initial balance") {
        val validator = Validation.buildDefaultValidatorFactory().validator

        val violations = validator.validate(PointProperties(initialBalance = 0L))

        violations.map { it.propertyPath.toString() } shouldContain "initialBalance"
    }

    test("getBalance returns the stored balance when a point row exists") {
        val user = User(id = 1L, role = Role.GUEST, provider = AuthProviderType.NONE, name = "Guest")
        whenever(userPointRepository.findByUserId(1L)).thenReturn(UserPoint(user = user, balance = 1350L))

        userPointService.getBalance(1L) shouldBe 1350L
    }

    test("getBalance returns zero when the point row is missing") {
        whenever(userPointRepository.findByUserId(1L)).thenReturn(null)

        userPointService.getBalance(1L) shouldBe 0L
    }

    test("getHistory delegates to the repository with the given pageable and returns the page") {
        val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "id"))
        val txn = PointTransaction(
            userId = 1L,
            delta = 100L,
            balanceAfter = 1350L,
            reason = PointTransactionReason.ATTENDANCE,
            idempotencyKey = "key-1",
        )
        val page = PageImpl(listOf(txn), pageable, 1L)
        whenever(pointTransactionRepository.findByUserId(1L, pageable)).thenReturn(page)

        userPointService.getHistory(1L, pageable) shouldBe page
    }
})
