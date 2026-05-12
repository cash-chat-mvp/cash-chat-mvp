package com.wnl.cashchat.api.domain.chat.exception

import java.util.UUID

class ConversationNotFoundException(
    val conversationUuid: UUID,
) : RuntimeException("Conversation not found: $conversationUuid")
