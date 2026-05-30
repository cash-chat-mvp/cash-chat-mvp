package com.wnl.cashchat.api.domain.point.persistence.repository

import com.wnl.cashchat.api.domain.point.persistence.entity.PointTransaction
import org.springframework.data.jpa.repository.JpaRepository

interface PointTransactionRepository : JpaRepository<PointTransaction, Long> {
    fun findByIdempotencyKey(idempotencyKey: String): PointTransaction?
}
