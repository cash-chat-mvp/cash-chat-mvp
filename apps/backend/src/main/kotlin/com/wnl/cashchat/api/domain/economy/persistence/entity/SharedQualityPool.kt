package com.wnl.cashchat.api.domain.economy.persistence.entity

import com.wnl.cashchat.api.common.entity.BaseEntity
import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(name = "shared_quality_pool")
class SharedQualityPool(
    @Id val id: Long = SINGLETON_ID,
    @Column(name = "balance", nullable = false, precision = 18, scale = 4)
    var balance: BigDecimal = BigDecimal.ZERO,
) : BaseEntity() {
    companion object { const val SINGLETON_ID = 1L }
}
