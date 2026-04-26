package com.wnl.cashchat.api.domain.chat.persistence.repository

import com.wnl.cashchat.api.domain.chat.persistence.entity.Conversation
import org.springframework.data.jpa.repository.JpaRepository

interface ConversationRepository : JpaRepository<Conversation, Long>
