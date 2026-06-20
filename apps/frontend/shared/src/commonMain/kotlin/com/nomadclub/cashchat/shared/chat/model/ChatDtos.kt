package com.nomadclub.cashchat.shared.chat.model

import kotlinx.serialization.Serializable

@Serializable
data class ConversationDto(
    val conversationId: Long,
    val title: String,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class ConversationSummaryDto(
    val conversationId: Long,
    val title: String,
    val lastMessage: String? = null,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class ChatMessageDto(
    val messageId: Long,
    val role: String,      // "USER" | "ASSISTANT"
    val content: String,
    val status: String,
    val createdAt: String,
)

@Serializable
data class CreateConversationRequest(val title: String? = null)

@Serializable
data class ChatStreamRequest(val conversationId: Long, val message: String)
