package com.wnl.cashchat.api.domain.chat.service.llm

import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage

internal fun LlmMessage.toSpringAiMessage(): Message =
    when (role) {
        LlmMessageRole.SYSTEM -> SystemMessage(content)
        LlmMessageRole.USER -> UserMessage(content)
        LlmMessageRole.ASSISTANT -> AssistantMessage(content)
    }
