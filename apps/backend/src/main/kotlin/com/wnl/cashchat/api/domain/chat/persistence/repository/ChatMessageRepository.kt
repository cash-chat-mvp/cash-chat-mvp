package com.wnl.cashchat.api.domain.chat.persistence.repository

import com.wnl.cashchat.api.domain.chat.persistence.entity.ChatMessage
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ChatMessageRepository : JpaRepository<ChatMessage, Long> {
    fun findAllByConversationIdOrderByCreatedAtAsc(conversationId: Long): List<ChatMessage>

    fun findTopByConversationIdOrderByCreatedAtDesc(conversationId: Long): ChatMessage?

    @Query(
        """
        select message
        from ChatMessage message
        join fetch message.conversation conversation
        where conversation.id in :conversationIds
          and message.id = (
              select max(latest.id)
              from ChatMessage latest
              where latest.conversation.id = conversation.id
          )
        """
    )
    fun findLatestByConversationIds(
        @Param("conversationIds") conversationIds: List<Long>,
    ): List<ChatMessage>
}
