package com.wnl.cashchat.api.domain.ledger.persistence.repository

import com.wnl.cashchat.api.domain.ledger.persistence.entity.LedgerEntry
import org.springframework.data.jpa.repository.JpaRepository

interface LedgerEntryRepository : JpaRepository<LedgerEntry, Long> {
    fun findByIdempotencyKey(key: String): LedgerEntry?
}
