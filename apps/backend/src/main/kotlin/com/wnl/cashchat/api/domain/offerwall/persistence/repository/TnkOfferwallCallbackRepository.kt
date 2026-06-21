package com.wnl.cashchat.api.domain.offerwall.persistence.repository

import com.wnl.cashchat.api.domain.offerwall.persistence.entity.OfferwallPlatform
import com.wnl.cashchat.api.domain.offerwall.persistence.entity.TnkOfferwallCallback
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface TnkOfferwallCallbackRepository : JpaRepository<TnkOfferwallCallback, Long> {
    fun findByPlatformAndSeqId(platform: OfferwallPlatform, seqId: String): TnkOfferwallCallback?

    /**
     * (platform, seq_id) 행을 PENDING 으로 멱등 생성한다. 이미 있으면 no-op(ON DUPLICATE KEY UPDATE)으로
     * 예외를 던지지 않아 메인 트랜잭션이 오염되지 않는다. platform 은 enum name 문자열로 전달한다.
     */
    @Modifying
    @Query(
        value = "INSERT INTO tnk_offerwall_callbacks " +
            "(platform, seq_id, md_user_nm, pay_pnt, coin_amount, user_id, status, raw_query, created_at, updated_at) " +
            "VALUES (:platform, :seqId, :mdUserNm, :payPnt, 0, NULL, 'PENDING', :rawQuery, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)) " +
            "ON DUPLICATE KEY UPDATE seq_id = seq_id",
        nativeQuery = true,
    )
    fun insertIfAbsent(
        @Param("platform") platform: String,
        @Param("seqId") seqId: String,
        @Param("mdUserNm") mdUserNm: String,
        @Param("payPnt") payPnt: Long,
        @Param("rawQuery") rawQuery: String,
    ): Int

    /**
     * (platform, seq_id) 행을 비관적 쓰기 락으로 조회한다. 동일 (platform, seq_id) 동시 콜백을 직렬화한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from TnkOfferwallCallback c where c.platform = :platform and c.seqId = :seqId")
    fun findForUpdate(
        @Param("platform") platform: OfferwallPlatform,
        @Param("seqId") seqId: String,
    ): TnkOfferwallCallback?
}
