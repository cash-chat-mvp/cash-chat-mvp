package com.wnl.cashchat.api.domain.economy.persistence.repository

import com.wnl.cashchat.api.domain.economy.persistence.entity.SharedQualityPool
import org.springframework.data.jpa.repository.*
import org.springframework.data.repository.query.Param
import java.math.BigDecimal

interface SharedQualityPoolRepository : JpaRepository<SharedQualityPool, Long> {
    @Modifying
    @Query(
        value = "INSERT INTO shared_quality_pool (id, balance, created_at, updated_at) " +
            "VALUES (1, 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)) " +
            "ON DUPLICATE KEY UPDATE id = id",
        nativeQuery = true,
    )
    fun insertSingletonIfAbsent(): Int

    @Modifying
    @Query(
        value = "UPDATE shared_quality_pool SET balance = balance + :amount, updated_at = CURRENT_TIMESTAMP(6) WHERE id = 1",
        nativeQuery = true,
    )
    fun accrue(@Param("amount") amount: BigDecimal): Int
}
