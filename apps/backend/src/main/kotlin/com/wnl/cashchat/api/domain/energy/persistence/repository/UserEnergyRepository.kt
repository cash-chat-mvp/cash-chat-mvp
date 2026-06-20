package com.wnl.cashchat.api.domain.energy.persistence.repository

import com.wnl.cashchat.api.domain.energy.persistence.entity.UserEnergy
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserEnergyRepository : JpaRepository<UserEnergy, Long> {
    fun findByUserId(userId: Long): UserEnergy?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from UserEnergy e where e.user.id = :userId")
    fun findByUserIdForUpdate(@Param("userId") userId: Long): UserEnergy?
}
