package com.wnl.cashchat.api.domain.offerwall.persistence.repository

import com.wnl.cashchat.api.domain.offerwall.persistence.entity.TnkOfferwallCallback
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface TnkOfferwallCallbackRepository : JpaRepository<TnkOfferwallCallback, Long> {
    fun findBySeqId(seqId: String): TnkOfferwallCallback?

    /**
     * seq_id 행을 PENDING 으로 멱등 생성한다. 이미 있으면 no-op(ON DUPLICATE KEY UPDATE)으로 예외를 던지지 않아
     * 메인 트랜잭션이 오염되지 않고, 엔티티를 로드하지 않으므로 findForUpdate 가 행을 락과 함께 최신 상태로 로드한다.
     */
    @Modifying
    @Query(
        value = "INSERT INTO tnk_offerwall_callbacks " +
            "(seq_id, md_user_nm, pay_pnt, coin_amount, user_id, status, raw_query, created_at, updated_at) " +
            "VALUES (:seqId, :mdUserNm, :payPnt, 0, NULL, 'PENDING', :rawQuery, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)) " +
            "ON DUPLICATE KEY UPDATE seq_id = seq_id",
        nativeQuery = true,
    )
    fun insertIfAbsent(
        @Param("seqId") seqId: String,
        @Param("mdUserNm") mdUserNm: String,
        @Param("payPnt") payPnt: Long,
        @Param("rawQuery") rawQuery: String,
    ): Int

    /**
     * seq_id 행을 비관적 쓰기 락으로 조회한다. 동일 seq_id 동시 콜백을 직렬화해, 뒤 트랜잭션이 최신 status 를
     * 읽도록 보장 → PENDING 1건만 적립하고 나머지는 GRANTED/REJECTED 를 그대로 멱등 반환한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from TnkOfferwallCallback c where c.seqId = :seqId")
    fun findForUpdate(@Param("seqId") seqId: String): TnkOfferwallCallback?
}
