package com.wnl.cashchat.api.domain.quality.persistence.repository

import com.wnl.cashchat.api.domain.quality.persistence.entity.DailyPremiumUsage
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface DailyPremiumUsageRepository : JpaRepository<DailyPremiumUsage, Long> {

    fun findByUserIdAndUsageDate(userId: Long, usageDate: LocalDate): DailyPremiumUsage?
}
