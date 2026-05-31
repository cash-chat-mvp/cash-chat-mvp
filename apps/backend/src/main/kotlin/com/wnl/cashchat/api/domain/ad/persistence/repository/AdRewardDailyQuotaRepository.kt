package com.wnl.cashchat.api.domain.ad.persistence.repository

import com.wnl.cashchat.api.domain.ad.persistence.entity.AdRewardDailyQuota
import com.wnl.cashchat.api.domain.ad.persistence.entity.AdRewardDailyQuotaId
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface AdRewardDailyQuotaRepository : JpaRepository<AdRewardDailyQuota, AdRewardDailyQuotaId> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from AdRewardDailyQuota q where q.userId = :userId and q.kstDate = :kstDate")
    fun findForUpdate(@Param("userId") userId: Long, @Param("kstDate") kstDate: LocalDate): AdRewardDailyQuota?

    fun findByUserIdAndKstDate(userId: Long, kstDate: LocalDate): AdRewardDailyQuota?

    /**
     * (userId, kstDate) 행을 멱등하게 생성한다. 이미 있으면 no-op(ON DUPLICATE KEY UPDATE)으로 예외를 던지지 않아,
     * 메인 트랜잭션에서 직접 호출해도 영속성 컨텍스트가 오염되지 않는다. 엔티티를 로드하지 않으므로 뒤따르는
     * findForUpdate 가 행을 락과 함께 최신 상태로 처음 로드한다. (MySQL·H2 MySQL 모드 모두 지원)
     */
    @Modifying
    @Query(
        value = "INSERT INTO ad_reward_daily_quota (user_id, kst_date, used_count, created_at, updated_at) " +
            "VALUES (:userId, :kstDate, 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)) " +
            "ON DUPLICATE KEY UPDATE used_count = used_count",
        nativeQuery = true,
    )
    fun insertIfAbsent(@Param("userId") userId: Long, @Param("kstDate") kstDate: LocalDate): Int
}
