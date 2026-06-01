package com.wnl.cashchat.api.domain.ad.persistence.repository

import com.wnl.cashchat.api.domain.ad.persistence.entity.AdRewardNonce
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AdRewardNonceRepository : JpaRepository<AdRewardNonce, String> {
    /**
     * nonce 행을 비관적 쓰기 락으로 조회한다. 동일 nonce 로 거의 동시에 들어오는 적립 요청을 직렬화해,
     * 무락 조회 시 stale 1차 캐시(used=false)로 인한 중복 적립(double spending)을 차단한다.
     * 락 순서는 nonce → 일일 한도 → user_point 로 일관되게 유지해 데드락을 피한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from AdRewardNonce n where n.nonce = :nonce")
    fun findForUpdate(@Param("nonce") nonce: String): AdRewardNonce?
}
