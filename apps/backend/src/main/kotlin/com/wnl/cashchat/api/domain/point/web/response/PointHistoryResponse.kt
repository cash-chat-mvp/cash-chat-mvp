package com.wnl.cashchat.api.domain.point.web.response

import com.wnl.cashchat.api.domain.point.persistence.entity.PointTransaction
import com.wnl.cashchat.api.domain.point.persistence.entity.PointTransactionReason
import org.springframework.data.domain.Page
import java.time.Instant

data class PointHistoryItemResponse(
    val delta: Long,
    val balanceAfter: Long,
    val reason: PointTransactionReason,
    val createdAt: Instant,
) {
    companion object {
        fun from(transaction: PointTransaction): PointHistoryItemResponse =
            PointHistoryItemResponse(
                delta = transaction.delta,
                balanceAfter = transaction.balanceAfter,
                reason = transaction.reason,
                createdAt = transaction.createdAt,
            )
    }
}

data class PointHistoryResponse(
    val content: List<PointHistoryItemResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
) {
    companion object {
        fun from(page: Page<PointTransaction>): PointHistoryResponse =
            PointHistoryResponse(
                content = page.content.map(PointHistoryItemResponse::from),
                page = page.number,
                size = page.size,
                totalElements = page.totalElements,
                totalPages = page.totalPages,
                hasNext = page.hasNext(),
            )
    }
}
