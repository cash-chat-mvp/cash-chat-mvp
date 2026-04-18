package com.wnl.cashchat.api.domain.chat.persistence.repository

import com.wnl.cashchat.api.domain.chat.persistence.entity.ChatMessage
import org.springframework.data.jpa.repository.JpaRepository

interface ChatMessageRepository : JpaRepository<ChatMessage, Long> {
    fun findAllByConversationIdOrderByCreatedAtAsc(conversationId: Long): List<ChatMessage>
}
