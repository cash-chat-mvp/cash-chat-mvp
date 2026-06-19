package com.wnl.cashchat.api.domain.offerwall.persistence.repository

import com.wnl.cashchat.api.domain.offerwall.persistence.entity.OfferwallUserToken
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface OfferwallUserTokenRepository : JpaRepository<OfferwallUserToken, Long> {
    fun findByUserId(userId: Long): OfferwallUserToken?
    fun findByToken(token: String): OfferwallUserToken?

    /**
     * user_id 행을 잠금 읽기로 조회한다. insertIfAbsent 직후의 확정 조회에 사용 — 잠금 읽기는
     * REPEATABLE READ 스냅샷이 아닌 최신 커밋값을 읽으므로, 동시 최초 호출에서 다른 트랜잭션이
     * 먼저 커밋한 토큰 행을 누락 없이 본다(단일 생성 보장).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from OfferwallUserToken t where t.userId = :userId")
    fun findForUpdate(@Param("userId") userId: Long): OfferwallUserToken?

    /**
     * (user_id) 행을 멱등하게 생성한다. 이미 있으면 no-op(ON DUPLICATE KEY UPDATE)으로 예외를 던지지 않아,
     * saveAndFlush 의 DataIntegrityViolationException 이 상위 트랜잭션을 rollback-only 로 만드는 문제를 피한다.
     * 동시 최초 호출이 서로 다른 토큰을 만들어도 user_id PK 충돌로 한 행만 남는다(먼저 커밋한 토큰이 승리).
     */
    @Modifying
    @Query(
        value = "INSERT INTO offerwall_user_tokens (user_id, token, created_at, updated_at) " +
            "VALUES (:userId, :token, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)) " +
            "ON DUPLICATE KEY UPDATE user_id = user_id",
        nativeQuery = true,
    )
    fun insertIfAbsent(@Param("userId") userId: Long, @Param("token") token: String): Int
}
