package com.wnl.cashchat.api.domain.point.service

import com.wnl.cashchat.api.domain.point.exception.InsufficientPointsException
import com.wnl.cashchat.api.domain.point.persistence.entity.PointTransaction
import com.wnl.cashchat.api.domain.point.persistence.entity.PointTransactionReason
import com.wnl.cashchat.api.domain.point.persistence.entity.UserPoint
import com.wnl.cashchat.api.domain.point.persistence.repository.PointTransactionRepository
import com.wnl.cashchat.api.domain.point.persistence.repository.UserPointRepository
import com.wnl.cashchat.api.domain.point.properties.PointProperties
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserPointService(
    private val userPointRepository: UserPointRepository,
    private val pointTransactionRepository: PointTransactionRepository,
    private val pointProperties: PointProperties,
) {
    fun hasEnoughBalance(userId: Long): Boolean =
        userPointRepository.existsByUserIdAndBalanceGreaterThanEqual(userId, REQUIRED_STREAM_POINTS)

    @Transactional(readOnly = true)
    fun getBalance(userId: Long): Long =
        userPointRepository.findByUserId(userId)?.balance ?: 0L

    @Transactional(readOnly = true)
    fun getHistory(userId: Long, pageable: Pageable): Page<PointTransaction> =
        pointTransactionRepository.findByUserId(userId, pageable)

    fun ensureInitialized(user: User): UserPoint =
        userPointRepository.findByUserId(user.id)
            ?: createInitialPoint(user)

    /**
     * 멱등성 키 기반 포인트 적립/차감.
     *
     * 단일 트랜잭션 안에서 (1) 사용자 포인트 행을 비관적 락으로 잡고 → (2) 멱등성 키를 조회해
     * 이미 처리됐으면 기존 원장을 그대로 반환(중복 적립 방지) → (3) 잔액을 가감 → (4) 원장 INSERT.
     * 잠금-먼저 순서이므로 같은 키/같은 사용자에 동시 호출이 와도 행 락으로 직렬화되어
     * 두 번째 호출은 첫 번째가 커밋한 원장을 보고 그대로 반환한다(이중 적립 없음).
     * 유니크 제약 uq_point_transaction_idempotency_key가 최종 방어선이다.
     *
     * @param delta 양수=적립, 음수=차감. 차감 시 잔액 부족이면 InsufficientPointsException.
     */
    @Transactional
    fun recordTransaction(
        userId: Long,
        delta: Long,
        reason: PointTransactionReason,
        idempotencyKey: String,
    ): PointTransaction {
        val userPoint = userPointRepository.findByUserIdForUpdate(userId)
            ?: throw IllegalStateException("UserPoint not initialized for userId=$userId")

        pointTransactionRepository.findByIdempotencyKey(idempotencyKey)?.let { return it }

        if (delta >= 0) {
            userPoint.charge(delta)
        } else {
            val cost = -delta
            if (userPoint.balance < cost) throw InsufficientPointsException()
            userPoint.deduct(cost)
        }

        return pointTransactionRepository.save(
            PointTransaction(
                userId = userId,
                delta = delta,
                balanceAfter = userPoint.balance,
                reason = reason,
                idempotencyKey = idempotencyKey,
            )
        )
    }

    private fun createInitialPoint(user: User): UserPoint =
        try {
            userPointRepository.saveAndFlush(
                UserPoint(
                    user = user,
                    balance = pointProperties.initialBalance,
                )
            )
        } catch (e: DataIntegrityViolationException) {
            userPointRepository.findByUserId(user.id) ?: throw e
        }

    private companion object {
        private const val REQUIRED_STREAM_POINTS = 1L
    }
}
