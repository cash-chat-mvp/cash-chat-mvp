package com.wnl.cashchat.api.domain.chat.web.response

import com.wnl.cashchat.api.domain.chat.persistence.entity.SettlementStatus
import java.time.Instant

data class MessageSettlementResponse(
    val messageId: String,
    val chatStatus: String?,
    val settlementStatus: SettlementStatus,
    val energyDelta: Long,
    val pendingCashablePtDelta: Long,
    val evolutionExpDelta: Long,
    val settledAt: Instant?,
)
