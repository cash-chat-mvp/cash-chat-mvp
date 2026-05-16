package com.wnl.cashchat.api.domain.chat.persistence.repository

import com.wnl.cashchat.api.domain.chat.persistence.entity.Conversation
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ConversationRepository : JpaRepository<Conversation, Long> {
    fun findByUuid(uuid: UUID): Conversation?

    fun findAllByUserIdOrderByUpdatedAtDesc(userId: Long): List<Conversation>

    fun findByIdAndUserId(id: Long, userId: Long): Conversation?
}
