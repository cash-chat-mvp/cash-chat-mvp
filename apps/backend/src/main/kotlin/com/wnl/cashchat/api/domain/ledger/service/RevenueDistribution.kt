package com.wnl.cashchat.api.domain.ledger.service

/**
 * 수익 분배 결과 DTO. LedgerService.recordRevenue 의 반환값이자
 * LedgerEntry 에서 복원되는 읽기 전용 뷰다.
 */
data class RevenueDistribution(
    val grossRevenue: Long,
    val riskReserve: Long,
    val serviceReserve: Long,
    val companyProfit: Long,
    val cashablePt: Long,
    val energy: Int,
)
