package com.wnl.cashchat.api.domain.ad.persistence.entity

import com.wnl.cashchat.api.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.time.LocalDate

/**
 * per-user-per-day 광고 시청 카운터. SSV 적립 트랜잭션 안에서 SELECT … FOR UPDATE 로 락을 잡는다.
 */
@Entity
@IdClass(AdRewardDailyQuotaId::class)
@Table(name = "ad_reward_daily_quota")
class AdRewardDailyQuota(
    @Id
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Id
    @Column(name = "kst_date", nullable = false)
    val kstDate: LocalDate,

    usedCount: Int = 0,
) : BaseEntity() {
    @Column(name = "used_count", nullable = false)
    var usedCount: Int = usedCount
        private set

    fun increment() {
        usedCount += 1
    }
}
