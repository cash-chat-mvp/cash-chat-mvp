package com.wnl.cashchat.api.domain.evolution.persistence.repository

import com.wnl.cashchat.api.domain.evolution.persistence.entity.UserEvolution
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserEvolutionRepository : JpaRepository<UserEvolution, Long> {
    fun findByUserId(userId: Long): UserEvolution?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from UserEvolution e where e.user.id = :userId")
    fun findByUserIdForUpdate(@Param("userId") userId: Long): UserEvolution?
}