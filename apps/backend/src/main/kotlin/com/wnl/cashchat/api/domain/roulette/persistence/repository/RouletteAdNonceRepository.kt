package com.wnl.cashchat.api.domain.roulette.persistence.repository

import com.wnl.cashchat.api.domain.roulette.persistence.entity.RouletteAdNonce
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface RouletteAdNonceRepository : JpaRepository<RouletteAdNonce, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from RouletteAdNonce n where n.nonce = :nonce")
    fun findForUpdate(@Param("nonce") nonce: String): RouletteAdNonce?
}
