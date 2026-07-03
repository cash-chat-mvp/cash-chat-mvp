package com.wnl.cashchat.api.domain.roulette.persistence.entity

import com.wnl.cashchat.api.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate

@Entity
@Table(
    name = "roulette_daily_state",
    uniqueConstraints = [UniqueConstraint(name = "uq_roulette_daily_state_user_date", columnNames = ["user_id", "kst_date"])]
)
class RouletteDailyState(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "kst_date", nullable = false)
    val kstDate: LocalDate,

    spinsUsed: Int = 0,

    freeSpinsUsed: Int = 0,
) : BaseEntity() {
    @Column(name = "spins_used", nullable = false)
    var spinsUsed: Int = spinsUsed
        private set

    @Column(name = "free_spins_used", nullable = false)
    var freeSpinsUsed: Int = freeSpinsUsed
        private set

    fun recordFreeSpin() {
        spinsUsed += 1
        freeSpinsUsed += 1
    }

    fun recordAdSpin() {
        spinsUsed += 1
    }

    init {
        require(spinsUsed >= 0) { "spinsUsed must be non-negative" }
        require(freeSpinsUsed >= 0) { "freeSpinsUsed must be non-negative" }
    }
}
