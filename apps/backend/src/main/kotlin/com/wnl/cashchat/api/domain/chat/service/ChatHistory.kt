package com.wnl.cashchat.api.domain.chat.service

import com.wnl.cashchat.api.domain.chat.persistence.entity.ChatMessage
import java.util.UUID

data class ChatHistory(
    val conversationUuid: UUID,
    val messages: List<ChatMessage>,
)
