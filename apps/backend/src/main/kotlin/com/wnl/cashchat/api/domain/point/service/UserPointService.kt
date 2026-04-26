package com.wnl.cashchat.api.domain.point.service

import com.wnl.cashchat.api.domain.point.persistence.entity.UserPoint
import com.wnl.cashchat.api.domain.point.persistence.repository.UserPointRepository
import com.wnl.cashchat.api.domain.point.properties.PointProperties
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service

@Service
class UserPointService(
    private val userPointRepository: UserPointRepository,
    private val pointProperties: PointProperties,
) {
    fun hasEnoughBalance(userId: Long): Boolean =
        userPointRepository.existsByUserIdAndBalanceGreaterThanEqual(userId, REQUIRED_STREAM_POINTS)

    fun ensureInitialized(user: User): UserPoint =
        userPointRepository.findByUserId(user.id)
            ?: createInitialPoint(user)

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
