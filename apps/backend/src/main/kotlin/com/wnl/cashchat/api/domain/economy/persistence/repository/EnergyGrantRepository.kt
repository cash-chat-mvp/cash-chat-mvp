package com.wnl.cashchat.api.domain.economy.persistence.repository

import com.wnl.cashchat.api.domain.economy.persistence.entity.EnergyGrant
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface EnergyGrantRepository : JpaRepository<EnergyGrant, Long> {
    @Query(
        """
        select g from EnergyGrant g
        where g.userId = :userId and g.remainingAmount > 0 and g.expiresAt > :now
        order by g.expiresAt asc
        """
    )
    fun findUsableOrderByExpiry(@Param("userId") userId: Long, @Param("now") now: Instant): List<EnergyGrant>
}
