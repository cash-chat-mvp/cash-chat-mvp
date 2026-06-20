package com.wnl.cashchat.api.domain.ad.persistence.repository

import com.wnl.cashchat.api.domain.ad.persistence.entity.GoogleAdSsvEvent
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface GoogleAdSsvEventRepository : JpaRepository<GoogleAdSsvEvent, Long> {
    fun findByTransactionId(transactionId: String): GoogleAdSsvEvent?

    /**
     * 이벤트 행을 비관적 쓰기 락으로 조회한다. 동일 transactionId 콜백이 동시에 인입될 때 적립 트랜잭션을
     * 직렬화해, 한쪽이 GRANTED 로 커밋한 뒤 다른 쪽이 stale 상태를 REJECTED 로 덮어쓰는 레이스를 막는다.
     * 락 순서는 event → nonce → ad_reward_daily_quota → user_point 로 일관 유지한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from GoogleAdSsvEvent e where e.transactionId = :transactionId")
    fun findForUpdateByTransactionId(@Param("transactionId") transactionId: String): GoogleAdSsvEvent?
}
