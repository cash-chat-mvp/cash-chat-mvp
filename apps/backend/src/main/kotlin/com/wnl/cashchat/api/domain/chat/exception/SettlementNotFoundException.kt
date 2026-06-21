package com.wnl.cashchat.api.domain.chat.exception

class SettlementNotFoundException(val messageId: String) : RuntimeException("Settlement not found: $messageId")
