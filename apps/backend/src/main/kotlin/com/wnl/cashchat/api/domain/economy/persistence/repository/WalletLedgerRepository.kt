package com.wnl.cashchat.api.domain.economy.persistence.repository

import com.wnl.cashchat.api.domain.economy.persistence.entity.WalletLedger
import org.springframework.data.jpa.repository.JpaRepository

interface WalletLedgerRepository : JpaRepository<WalletLedger, Long> {
    fun findByIdempotencyKey(idempotencyKey: String): WalletLedger?
}
