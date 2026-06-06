package com.wnl.cashchat.api.domain.quality.persistence.entity

import com.wnl.cashchat.api.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate

/**
 * 유저별 일일 프리미엄 사용 횟수.
 * uq(user_id, usage_date) 제약으로 동시 INSERT 경합을 DB 레벨에서 방어한다.
 * 서비스 레이어에서 DataIntegrityViolationException 을 잡아 재조회 후 increment() 한다.
 */
@Entity
@Table(
    name = "daily_premium_usage",
    uniqueConstraints = [UniqueConstraint(name = "uq_daily_premium_usage_user_date", columnNames = ["user_id", "usage_date"])],
    indexes = [Index(name = "idx_daily_premium_usage_user", columnList = "user_id")],
)
class DailyPremiumUsage(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "usage_date", nullable = false)
    val usageDate: LocalDate,

    count: Int = 0,
) : BaseEntity() {

    @Column(nullable = false)
    var count: Int = count
        private set

    init {
        require(count >= 0) { "Daily premium usage count must be non-negative" }
    }

    constructor(userId: Long, usageDate: LocalDate, count: Int) : this(
        id = 0L,
        userId = userId,
        usageDate = usageDate,
        count = count,
    )

    fun increment() {
        count += 1
    }
}
