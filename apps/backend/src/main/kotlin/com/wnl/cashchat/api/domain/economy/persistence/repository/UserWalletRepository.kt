package com.wnl.cashchat.api.domain.economy.persistence.repository

import com.wnl.cashchat.api.domain.economy.persistence.entity.UserWallet
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserWalletRepository : JpaRepository<UserWallet, Long> {
    fun findByUserId(userId: Long): UserWallet?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from UserWallet w where w.user.id = :userId")
    fun findByUserIdForUpdate(@Param("userId") userId: Long): UserWallet?
}
