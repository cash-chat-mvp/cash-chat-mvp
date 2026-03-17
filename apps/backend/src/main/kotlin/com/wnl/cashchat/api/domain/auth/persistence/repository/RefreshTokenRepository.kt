package com.wnl.cashchat.api.domain.auth.persistence.repository

import com.wnl.cashchat.api.domain.auth.persistence.entity.RefreshToken
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {
    fun findByToken(token: String): RefreshToken?
    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.user.id = :userId")
    fun deleteByUserId(@Param("userId") userId: Long)

    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.token = :token")
    fun deleteByTokenReturningCount(@Param("token") token: String): Int

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RefreshToken r WHERE r.token = :token")
    fun findByTokenForUpdate(@Param("token") token: String): RefreshToken?
}
