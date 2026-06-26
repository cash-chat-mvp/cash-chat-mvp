package com.wnl.cashchat.api.domain.chat.persistence.repository

import com.wnl.cashchat.api.domain.chat.persistence.entity.ChatMessage
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface ChatMessageRepository : JpaRepository<ChatMessage, Long> {
    fun findAllByConversationIdOrderByCreatedAtAsc(conversationId: Long): List<ChatMessage>

    /**
     * assistant 메시지 행을 비관적 쓰기 락으로 조회한다. 스트림 종료 정산(finalizeAssistantMessage)이
     * 정상 완료와 취소(doOnCancel) 등으로 동시에 인입될 때 행을 직렬화해, STREAMING 상태 가드의
     * read-check-write 구간(TOCTOU)을 원자화하여 예약 밥 이중 정산·환불과 경험치 이중 적립을 막는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select message from ChatMessage message where message.id = :id")
    fun findForUpdateById(@Param("id") id: Long): Optional<ChatMessage>

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
