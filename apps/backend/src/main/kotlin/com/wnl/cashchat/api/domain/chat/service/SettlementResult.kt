// SettlementResult.kt
package com.wnl.cashchat.api.domain.chat.service
import com.wnl.cashchat.api.domain.chat.persistence.entity.SettlementStatus
import java.time.Instant
data class SettlementResult(
    val messageId: String, val status: SettlementStatus,
    val energyDelta: Long, val pendingPtDelta: Long, val evolutionExpDelta: Long,
    val energyBalance: Long, val pendingCashablePt: Long, val evolutionExp: Long,
    val settledAt: Instant?,
)
