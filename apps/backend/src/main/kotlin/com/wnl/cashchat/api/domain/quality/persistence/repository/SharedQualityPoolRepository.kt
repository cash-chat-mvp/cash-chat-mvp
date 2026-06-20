package com.wnl.cashchat.api.domain.quality.persistence.repository

import com.wnl.cashchat.api.domain.quality.persistence.entity.SharedQualityPool
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface SharedQualityPoolRepository : JpaRepository<SharedQualityPool, Long> {

    /** 비관락으로 싱글톤 풀 행(id=1)을 조회한다. accrue/tryConsume 전에 사용한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SharedQualityPool p where p.id = 1")
    fun findForUpdate(): SharedQualityPool?

    /** 읽기 전용 조회 (throttleScale 등). */
    @Query("select p from SharedQualityPool p where p.id = 1")
    fun findById1(): SharedQualityPool?
}
