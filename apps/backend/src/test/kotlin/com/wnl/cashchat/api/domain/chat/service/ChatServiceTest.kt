package com.wnl.cashchat.api.domain.chat.service

import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.chat.persistence.entity.ChatMessage
import com.wnl.cashchat.api.domain.chat.persistence.entity.Conversation
import com.wnl.cashchat.api.domain.chat.persistence.entity.MessageRole
import com.wnl.cashchat.api.domain.chat.persistence.entity.MessageStatus
import com.wnl.cashchat.api.domain.chat.persistence.repository.ChatMessageRepository
import com.wnl.cashchat.api.domain.chat.persistence.repository.ConversationRepository
import com.wnl.cashchat.api.domain.chat.service.llm.LlmMessage
import com.wnl.cashchat.api.domain.chat.service.llm.LlmMessageRole
import com.wnl.cashchat.api.domain.chat.service.llm.LlmProvider
import com.wnl.cashchat.api.domain.user.persistence.entity.Role
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class ChatServiceTest {

    @Mock
    lateinit var conversationRepository: ConversationRepository

    @Mock
    lateinit var chatMessageRepository: ChatMessageRepository

    @Mock
    lateinit var llmProvider: LlmProvider

    @InjectMocks
    lateinit var chatService: ChatService

    @Test
    fun `stream rejects conversations owned by another user`() {
        val owner = User(id = 2L, role = Role.MEMBER, provider = AuthProviderType.NONE, name = "owner")
        val conversation = Conversation(id = 1L, user = owner, title = null)

        whenever(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation))

        assertThrows<IllegalArgumentException> {
            chatService.stream(userId = 99L, conversationId = 1L, content = "hello")
        }
    }

    @Test
    fun `stream saves user message and completes assistant message`() {
        val user = User(id = 1L, role = Role.MEMBER, provider = AuthProviderType.NONE, name = "owner")
        val conversation = Conversation(id = 1L, user = user, title = null)

        whenever(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation))
        whenever(chatMessageRepository.findAllByConversationIdOrderByCreatedAtAsc(1L)).thenReturn(emptyList())
        whenever(chatMessageRepository.save(any<ChatMessage>())).thenAnswer { it.arguments[0] as ChatMessage }
        whenever(llmProvider.stream(any())).thenReturn(Flux.just("hi", " there"))

        StepVerifier.create(chatService.stream(userId = 1L, conversationId = 1L, content = "hello"))
            .expectNext("hi", " there")
            .verifyComplete()

        verify(llmProvider).stream(
            argThat<List<LlmMessage>> { this == listOf(LlmMessage(LlmMessageRole.USER, "hello")) }
        )
        verify(chatMessageRepository, atLeastOnce()).save(
            argThat { role == MessageRole.ASSISTANT && status == MessageStatus.COMPLETED }
        )
    }
}
