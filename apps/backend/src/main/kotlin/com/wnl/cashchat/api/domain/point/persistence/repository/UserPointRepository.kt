package com.wnl.cashchat.api.domain.point.persistence.repository

import com.wnl.cashchat.api.domain.point.persistence.entity.UserPoint
import org.springframework.data.jpa.repository.JpaRepository

interface UserPointRepository : JpaRepository<UserPoint, Long> {
    fun findByUserId(userId: Long): UserPoint?

    fun existsByUserIdAndBalanceGreaterThanEqual(userId: Long, balance: Long): Boolean
}
