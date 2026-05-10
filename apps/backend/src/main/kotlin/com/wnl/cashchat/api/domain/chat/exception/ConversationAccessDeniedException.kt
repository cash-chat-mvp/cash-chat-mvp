package com.wnl.cashchat.api.domain.chat.exception

import java.util.UUID

class ConversationAccessDeniedException(
    val conversationUuid: UUID,
) : RuntimeException("Conversation does not belong to user: $conversationUuid")
