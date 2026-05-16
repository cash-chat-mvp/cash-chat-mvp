package com.wnl.cashchat.api.domain.chat.exception

import java.util.UUID

class ConversationNotFoundException : RuntimeException {
    val conversationUuid: UUID?
    val conversationId: Long?

    constructor(conversationUuid: UUID) : super("Conversation not found: $conversationUuid") {
        this.conversationUuid = conversationUuid
        this.conversationId = null
    }

    constructor(conversationId: Long) : super("Conversation not found: $conversationId") {
        this.conversationUuid = null
        this.conversationId = conversationId
    }
}
