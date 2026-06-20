package com.wnl.cashchat.api.domain.economy.persistence.entity

import com.wnl.cashchat.api.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "energy_grant", indexes = [Index(name = "idx_energy_grant_user", columnList = "user_id, expires_at")])
class EnergyGrant(
    @Column(name = "user_id", nullable = false) val userId: Long,
    @Enumerated(EnumType.STRING) @Column(name = "source_type", nullable = false, length = 30)
    val sourceType: EnergySourceType,
    @Column(name = "granted_amount", nullable = false) val grantedAmount: Long,
    @Column(name = "granted_at", nullable = false) val grantedAt: Instant,
    @Column(name = "expires_at", nullable = false) val expiresAt: Instant,
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
) : BaseEntity() {
    @Column(name = "remaining_amount", nullable = false)
    var remainingAmount: Long = grantedAmount
        private set

    fun consume(amount: Long): Long {
        require(amount >= 0) { "Consume amount must be non-negative" }
        val taken = minOf(amount, remainingAmount)
        remainingAmount -= taken
        return taken
    }
}
