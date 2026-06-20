package com.wnl.cashchat.api.domain.inventory.persistence.repository

import com.wnl.cashchat.api.domain.inventory.persistence.entity.UserInventory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserInventoryRepository : JpaRepository<UserInventory, Long> {

    fun findByUserIdOrderByItemCodeAsc(userId: Long): List<UserInventory>

    fun findByUserIdAndItemCode(userId: Long, itemCode: String): UserInventory?

    /**
     * 동시성 안전 UPSERT: (user_id, item_code) 가 있으면 qty 누적, 없으면 INSERT.
     * UPDATE 절에서 VALUES(qty) 대신 명명 파라미터 :qty 를 재사용해 MySQL 8 / H2(MySQL 모드) 모두 호환.
     * 네이티브 쓰기 후 영속성 컨텍스트를 flush+clear 해 이후 조회가 DB 최신값을 보게 한다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
            INSERT INTO user_inventory (user_id, item_code, qty, created_at, updated_at)
            VALUES (:userId, :itemCode, :qty, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
            ON DUPLICATE KEY UPDATE qty = qty + :qty, updated_at = CURRENT_TIMESTAMP(6)
        """,
        nativeQuery = true,
    )
    fun upsertQty(
        @Param("userId") userId: Long,
        @Param("itemCode") itemCode: String,
        @Param("qty") qty: Int,
    )
}
