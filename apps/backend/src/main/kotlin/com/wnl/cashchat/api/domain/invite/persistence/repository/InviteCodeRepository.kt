package com.wnl.cashchat.api.domain.invite.persistence.repository

import com.wnl.cashchat.api.domain.invite.persistence.entity.InviteCode
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

interface InviteCodeRepository : JpaRepository<InviteCode, Long> {
    fun findByUserId(userId: Long): InviteCode?
    fun findByCode(code: String): InviteCode?

    /**
     * (user_id) 행을 멱등 생성한다. user_id PK 가 이미 있으면 no-op(동시 최초 호출 한 행만 남음).
     * code UNIQUE 가 다른 사용자와 충돌하면 그 행에 no-op 이 적용되어 우리 행은 INSERT 되지 않으므로,
     * 호출 측은 직후 findForUpdate(userId) 가 null 인지로 코드 충돌을 감지해 재시도한다.
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(
        value = "INSERT INTO invite_codes (user_id, code, created_at, updated_at) " +
            "VALUES (:userId, :code, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)) " +
            "ON DUPLICATE KEY UPDATE user_id = user_id",
        nativeQuery = true,
    )
    fun insertIfAbsent(@Param("userId") userId: Long, @Param("code") code: String): Int

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from InviteCode c where c.userId = :userId")
    fun findForUpdate(@Param("userId") userId: Long): InviteCode?
}
