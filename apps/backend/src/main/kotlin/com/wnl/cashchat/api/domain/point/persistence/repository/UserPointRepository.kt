package com.wnl.cashchat.api.domain.point.persistence.repository

import com.wnl.cashchat.api.domain.point.persistence.entity.UserPoint
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserPointRepository : JpaRepository<UserPoint, Long> {
    fun findByUserId(userId: Long): UserPoint?

    fun existsByUserIdAndBalanceGreaterThanEqual(userId: Long, balance: Long): Boolean

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select up from UserPoint up where up.user.id = :userId")
    fun findByUserIdForUpdate(@Param("userId") userId: Long): UserPoint?
}
