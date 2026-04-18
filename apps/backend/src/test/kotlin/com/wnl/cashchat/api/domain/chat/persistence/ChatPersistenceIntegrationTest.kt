package com.wnl.cashchat.api.domain.chat.persistence

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.chat.persistence.entity.ChatMessage
import com.wnl.cashchat.api.domain.chat.persistence.entity.Conversation
import com.wnl.cashchat.api.domain.chat.persistence.entity.MessageRole
import com.wnl.cashchat.api.domain.chat.persistence.entity.MessageStatus
import com.wnl.cashchat.api.domain.chat.persistence.repository.ChatMessageRepository
import com.wnl.cashchat.api.domain.chat.persistence.repository.ConversationRepository
import com.wnl.cashchat.api.domain.user.persistence.entity.Role
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import com.wnl.cashchat.api.domain.user.persistence.repository.UserRepository
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest

@DataJpaTest
class ChatPersistenceIntegrationTest @Autowired constructor(
    private val userRepository: UserRepository,
    private val conversationRepository: ConversationRepository,
    private val chatMessageRepository: ChatMessageRepository,
) {

    @Test
    fun `conversation owner and ordered messages are persisted`() {
        val user = userRepository.save(
            User(
                role = Role.MEMBER,
                provider = AuthProviderType.NONE,
                name = "tester"
            )
        )

        val conversation = conversationRepository.save(
            Conversation(user = user, title = "first chat")
        )

        chatMessageRepository.save(
            ChatMessage(
                conversation = conversation,
                role = MessageRole.USER,
                content = "hello",
                status = MessageStatus.COMPLETED
            )
        )
        chatMessageRepository.save(
            ChatMessage(
                conversation = conversation,
                role = MessageRole.ASSISTANT,
                content = "hi there",
                status = MessageStatus.COMPLETED,
                model = "gemini-3.1-flash-lite-preview"
            )
        )

        val messages = chatMessageRepository.findAllByConversationIdOrderByCreatedAtAsc(conversation.id)

        messages shouldHaveSize 2
        messages[0].role shouldBe MessageRole.USER
        messages[1].role shouldBe MessageRole.ASSISTANT
        messages[1].model shouldBe "gemini-3.1-flash-lite-preview"
        conversation.user.id shouldBe user.id
    }
}
