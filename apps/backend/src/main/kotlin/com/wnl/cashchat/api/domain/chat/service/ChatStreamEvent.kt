package com.wnl.cashchat.api.domain.chat.service

sealed interface ChatStreamEvent {
    data class Meta(val messageId: String, val energyReserved: Long) : ChatStreamEvent
    data class Delta(val text: String) : ChatStreamEvent
    data class RewardSettled(val result: SettlementResult) : ChatStreamEvent
    data class Done(val finishReason: String) : ChatStreamEvent
}
