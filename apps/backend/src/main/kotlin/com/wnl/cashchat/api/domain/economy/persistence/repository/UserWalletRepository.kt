package com.wnl.cashchat.api.domain.economy.persistence.repository

import com.wnl.cashchat.api.domain.economy.persistence.entity.UserWallet
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserWalletRepository : JpaRepository<UserWallet, Long> {
    fun findByUserId(userId: Long): UserWallet?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from UserWallet w where w.user.id = :userId")
    fun findByUserIdForUpdate(@Param("userId") userId: Long): UserWallet?

    /**
     * (userId) 행을 멱등하게 생성한다. 이미 있으면 no-op(ON DUPLICATE KEY UPDATE)으로 예외를 던지지 않아,
     * 메인 트랜잭션에서 직접 호출해도 영속성 컨텍스트가 오염되지 않는다. 엔티티를 로드하지 않으므로 뒤따르는
     * findByUserIdForUpdate 가 행을 락과 함께 최신 상태로 처음 로드한다. (MySQL·H2 MySQL 모드 모두 지원)
     */
    @Modifying
    @Query(
        value = "INSERT INTO user_wallet " +
            "(user_id, energy_available, energy_reserved, pending_cashable_pt, confirmed_cashable_pt, " +
            "evolution_level, evolution_exp, evolution_fail_stack, created_at, updated_at) " +
            "VALUES (:userId, 0, 0, 0, 0, 1, 0, 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)) " +
            "ON DUPLICATE KEY UPDATE user_id = user_id",
        nativeQuery = true,
    )
    fun insertIfAbsent(@Param("userId") userId: Long): Int
}
