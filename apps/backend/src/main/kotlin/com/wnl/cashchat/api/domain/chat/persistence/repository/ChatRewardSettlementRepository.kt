package com.wnl.cashchat.api.domain.chat.persistence.repository

import com.wnl.cashchat.api.domain.chat.persistence.entity.ChatRewardSettlement
import com.wnl.cashchat.api.domain.chat.persistence.entity.ChatRewardType
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.*
import org.springframework.data.repository.query.Param

interface ChatRewardSettlementRepository : JpaRepository<ChatRewardSettlement, Long> {
    fun findByUserIdAndMessageIdAndRewardType(userId: Long, messageId: String, rewardType: ChatRewardType): ChatRewardSettlement?
    fun findByMessageId(messageId: String): ChatRewardSettlement?
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ChatRewardSettlement s where s.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): ChatRewardSettlement?
}
