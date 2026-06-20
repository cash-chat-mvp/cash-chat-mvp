package com.wnl.cashchat.api.domain.shop.service

import com.wnl.cashchat.api.domain.shop.persistence.entity.ShopItem
import com.wnl.cashchat.api.domain.shop.persistence.entity.ShopItemCategory
import com.wnl.cashchat.api.domain.shop.persistence.repository.ShopItemRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 카탈로그 조회. isActive=true + displayOrder 오름차순(쿼리 정렬).
 * Phase 1 은 ENHANCE 만 시드돼 있어 COSMETIC/VOUCHER 는 빈 리스트가 반환된다.
 * phase1Active 플래그는 응답 매퍼(ShopCatalogResponse)가 category 로부터 계산한다.
 */
@Service
class ShopCatalogService(
    private val shopItemRepository: ShopItemRepository,
) {
    @Transactional(readOnly = true)
    fun listItems(category: ShopItemCategory): List<ShopItem> =
        shopItemRepository.findByCategoryAndIsActiveTrueOrderByDisplayOrderAsc(category)
}
