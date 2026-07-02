package com.wnl.cashchat.api.domain.roulette.persistence.entity

import com.wnl.cashchat.api.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "roulette_ad_nonce")
class RouletteAdNonce(
    @Id
    @Column(name = "nonce", nullable = false, length = 64)
    val nonce: String,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    verified: Boolean = false,

    used: Boolean = false,

    transactionId: String? = null,
) : BaseEntity() {
    @Column(name = "verified", nullable = false)
    var verified: Boolean = verified
        private set

    @Column(name = "used", nullable = false)
    var used: Boolean = used
        private set

    @Column(name = "transaction_id", nullable = true, length = 128)
    var transactionId: String? = transactionId
        private set

    fun markVerified(transactionId: String) {
        verified = true
        this.transactionId = transactionId
    }

    fun markUsed() {
        used = true
    }

    fun isVerifiedAndUsable(now: Instant): Boolean = verified && !used && expiresAt.isAfter(now)
}
