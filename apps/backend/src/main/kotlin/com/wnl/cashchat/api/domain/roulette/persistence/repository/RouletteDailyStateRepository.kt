package com.wnl.cashchat.api.domain.roulette.persistence.repository

import com.wnl.cashchat.api.domain.roulette.persistence.entity.RouletteDailyState
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface RouletteDailyStateRepository : JpaRepository<RouletteDailyState, Long> {
    fun findByUserIdAndKstDate(userId: Long, kstDate: LocalDate): RouletteDailyState?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RouletteDailyState s where s.userId = :userId and s.kstDate = :kstDate")
    fun findForUpdate(@Param("userId") userId: Long, @Param("kstDate") kstDate: LocalDate): RouletteDailyState?

    @Modifying
    @Query(
        value = """
            INSERT INTO roulette_daily_state (user_id, kst_date, spins_used, free_spins_used, created_at, updated_at)
            VALUES (:userId, :kstDate, 0, 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
            ON DUPLICATE KEY UPDATE user_id = user_id
        """,
        nativeQuery = true
    )
    fun insertIfAbsent(@Param("userId") userId: Long, @Param("kstDate") kstDate: LocalDate)
}
