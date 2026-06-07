package com.wnl.cashchat.api.domain.inventory.service

import com.wnl.cashchat.api.domain.inventory.persistence.repository.UserInventoryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 인벤토리 읽기 API. 후속 진화/소모(consume) 시스템은 이 도메인에 consume 연산을 추가해 확장한다.
 */
@Service
class InventoryService(
    private val userInventoryRepository: UserInventoryRepository,
) {
    @Transactional(readOnly = true)
    fun getMine(userId: Long): List<InventoryLine> =
        userInventoryRepository.findByUserIdOrderByItemCodeAsc(userId)
            .map { InventoryLine(itemCode = it.itemCode, qty = it.qty) }
}
