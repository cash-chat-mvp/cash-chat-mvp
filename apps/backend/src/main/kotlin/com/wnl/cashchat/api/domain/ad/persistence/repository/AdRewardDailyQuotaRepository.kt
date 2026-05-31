package com.wnl.cashchat.api.domain.ad.persistence.repository

import com.wnl.cashchat.api.domain.ad.persistence.entity.AdRewardDailyQuota
import com.wnl.cashchat.api.domain.ad.persistence.entity.AdRewardDailyQuotaId
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface AdRewardDailyQuotaRepository : JpaRepository<AdRewardDailyQuota, AdRewardDailyQuotaId> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from AdRewardDailyQuota q where q.userId = :userId and q.kstDate = :kstDate")
    fun findForUpdate(@Param("userId") userId: Long, @Param("kstDate") kstDate: LocalDate): AdRewardDailyQuota?

    fun findByUserIdAndKstDate(userId: Long, kstDate: LocalDate): AdRewardDailyQuota?
}
