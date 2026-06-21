package com.wnl.cashchat.api.domain.chat.web.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.wnl.cashchat.api.common.security.jwt.JwtTokenHandler
import com.wnl.cashchat.api.domain.auth.persistence.entity.AuthProviderType
import com.wnl.cashchat.api.domain.chat.exception.ConversationAccessDeniedException
import com.wnl.cashchat.api.domain.chat.exception.ConversationNotFoundException
import com.wnl.cashchat.api.domain.chat.persistence.entity.ChatMessage
import com.wnl.cashchat.api.domain.chat.persistence.entity.Conversation
import com.wnl.cashchat.api.domain.chat.persistence.entity.MessageRole
import com.wnl.cashchat.api.domain.chat.persistence.entity.MessageStatus
import com.wnl.cashchat.api.domain.chat.service.ChatHistory
import com.wnl.cashchat.api.domain.chat.service.ChatService
import com.wnl.cashchat.api.domain.chat.web.exception.ChatExceptionHandler
import com.wnl.cashchat.api.domain.chat.web.response.ChatMessageResponse
import com.wnl.cashchat.api.domain.chat.web.response.ConversationResponse
import com.wnl.cashchat.api.domain.chat.web.response.ConversationSummaryResponse
import com.wnl.cashchat.api.domain.chat.service.ChatStreamEvent
import com.wnl.cashchat.api.domain.point.exception.InsufficientPointsException
import com.wnl.cashchat.api.domain.point.web.exception.PointExceptionHandler
import com.wnl.cashchat.api.domain.user.persistence.entity.Role
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import reactor.core.publisher.Flux
import java.time.Instant
import java.util.UUID

@WebMvcTest(ChatController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(PointExceptionHandler::class, ChatExceptionHandler::class)
class ChatControllerWebMvcTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @MockBean
    lateinit var chatService: ChatService

    @MockBean
    lateinit var jwtTokenHandler: JwtTokenHandler

    @MockBean(name = "jpaMappingContext")
    lateinit var jpaMappingContext: JpaMetamodelMappingContext

    init {
        test("create conversation endpoint returns a new conversation for authenticated user") {
            whenever(chatService.createConversation(1L, "English study tips")).thenReturn(
                ConversationResponse(
                    conversationId = 7L,
                    title = "English study tips",
                    createdAt = Instant.parse("2026-05-16T00:00:00Z"),
                    updatedAt = Instant.parse("2026-05-16T00:00:00Z"),
                )
            )

            mockMvc.perform(
                post("/api/v1/chat/conversations")
                    .principal(UsernamePasswordAuthenticationToken(1L, null))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(mapOf("title" to "English study tips")))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.conversationId").value(7))
                .andExpect(jsonPath("$.title").value("English study tips"))

            verify(chatService).createConversation(eq(1L), eq("English study tips"))
        }

        test("conversation list endpoint returns current user's rooms") {
            whenever(chatService.listConversations(1L)).thenReturn(
                listOf(
                    ConversationSummaryResponse(
                        conversationId = 7L,
                        title = "English study tips",
                        lastMessage = "Study a little every day",
                        createdAt = Instant.parse("2026-05-16T00:00:00Z"),
                        updatedAt = Instant.parse("2026-05-16T00:10:00Z"),
                    )
                )
            )

            mockMvc.perform(
                get("/api/v1/chat/conversations")
                    .principal(UsernamePasswordAuthenticationToken(1L, null))
                    .accept(MediaType.APPLICATION_JSON)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0].conversationId").value(7))
                .andExpect(jsonPath("$[0].title").value("English study tips"))
                .andExpect(jsonPath("$[0].lastMessage").value("Study a little every day"))

            verify(chatService).listConversations(eq(1L))
        }

        test("conversation messages endpoint returns selected room history") {
            whenever(chatService.getMessages(1L, 7L)).thenReturn(
                listOf(
                    ChatMessageResponse(
                        messageId = 10L,
                        role = "USER",
                        content = "English study tips",
                        status = "COMPLETED",
                        createdAt = Instant.parse("2026-05-16T00:01:00Z"),
                    )
                )
            )

            mockMvc.perform(
                get("/api/v1/chat/conversations/7/messages")
                    .principal(UsernamePasswordAuthenticationToken(1L, null))
                    .accept(MediaType.APPLICATION_JSON)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0].messageId").value(10))
                .andExpect(jsonPath("$[0].role").value("USER"))
                .andExpect(jsonPath("$[0].content").value("English study tips"))

            verify(chatService).getMessages(eq(1L), eq(7L))
        }

        test("conversation messages endpoint returns not found for unknown or foreign room") {
            whenever(chatService.getMessages(1L, 7L)).thenThrow(ConversationNotFoundException(7L))

            mockMvc.perform(
                get("/api/v1/chat/conversations/7/messages")
                    .principal(UsernamePasswordAuthenticationToken(1L, null))
                    .accept(MediaType.APPLICATION_JSON)
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.code").value("CONVERSATION_NOT_FOUND"))
        }

        test("chat stream endpoint returns text event stream for authenticated user") {
            whenever(chatService.stream(eq(1L), eq(7L), any(), eq("hello")))
                .thenReturn(Flux.just(ChatStreamEvent.Delta("hi there")))

            val result = mockMvc.perform(
                post("/api/v1/chat/stream")
                    .principal(UsernamePasswordAuthenticationToken(1L, null))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .content(objectMapper.writeValueAsString(mapOf("conversationId" to 7L, "messageId" to "msg-001", "message" to "hello")))
            )
                .andExpect(request().asyncStarted())
                .andReturn()

            mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk)
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string("event:message\ndata:hi there\n\n"))

            verify(chatService).stream(eq(1L), eq(7L), any(), eq("hello"))
        }

        test("chat stream endpoint rejects a missing conversation id") {
            mockMvc.perform(
                post("/api/v1/chat/stream")
                    .principal(UsernamePasswordAuthenticationToken(1L, null))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(mapOf("messageId" to "msg-001", "message" to "hello")))
            )
                .andExpect(status().isBadRequest)
        }

        test("chat stream endpoint rejects a blank message") {
            mockMvc.perform(
                post("/api/v1/chat/stream")
                    .principal(UsernamePasswordAuthenticationToken(1L, null))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(mapOf("conversationId" to 7L, "messageId" to "msg-001", "message" to "")))
            )
                .andExpect(status().isBadRequest)
        }

        test("chat stream endpoint sends a generic error message") {
            whenever(chatService.stream(eq(1L), eq(7L), any(), eq("hello")))
                .thenReturn(Flux.error(IllegalStateException("sensitive details")))

            val result = mockMvc.perform(
                post("/api/v1/chat/stream")
                    .principal(UsernamePasswordAuthenticationToken(1L, null))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .content(objectMapper.writeValueAsString(mapOf("conversationId" to 7L, "messageId" to "msg-001", "message" to "hello")))
            )
                .andExpect(request().asyncStarted())
                .andReturn()

            val response = mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk)
                .andReturn()
                .response
                .contentAsString

            response.contains("stream failed") shouldBe true
            response.contains("sensitive details") shouldBe false
        }

        test("chat stream endpoint returns payment required when points are insufficient") {
            whenever(chatService.stream(eq(1L), eq(7L), any(), eq("hello")))
                .thenThrow(InsufficientPointsException())

            mockMvc.perform(
                post("/api/v1/chat/stream")
                    .principal(UsernamePasswordAuthenticationToken(1L, null))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .content(objectMapper.writeValueAsString(mapOf("conversationId" to 7L, "messageId" to "msg-001", "message" to "hello")))
            )
                .andExpect(status().isPaymentRequired)
        }

        test("chat history endpoint returns ordered messages for authenticated user") {
            val conversationUuid = UUID.fromString("0c4fe408-6d7c-4bd9-b0f8-5fdbe2a6a6e8")
            val conversation = conversation(ownerId = 1L, uuid = conversationUuid)
            val createdAt = Instant.parse("2026-05-10T12:34:56Z")
            val message = ChatMessage(
                id = 10L,
                conversation = conversation,
                role = MessageRole.USER,
                content = "hello",
                status = MessageStatus.COMPLETED
            ).apply {
                this.createdAt = createdAt
            }

            whenever(chatService.getHistory(1L, conversationUuid))
                .thenReturn(ChatHistory(conversationUuid = conversationUuid, messages = listOf(message)))

            mockMvc.perform(
                get("/api/v1/chat/history/$conversationUuid")
                    .principal(UsernamePasswordAuthenticationToken(1L, null))
                    .accept(MediaType.APPLICATION_JSON)
            )
                .andExpect(status().isOk)
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.conversationUuid").value(conversationUuid.toString()))
                .andExpect(jsonPath("$.messages[0].id").value(10))
                .andExpect(jsonPath("$.messages[0].role").value("USER"))
                .andExpect(jsonPath("$.messages[0].content").value("hello"))
                .andExpect(jsonPath("$.messages[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.messages[0].createdAt").value("2026-05-10T12:34:56Z"))

            verify(chatService).getHistory(eq(1L), eq(conversationUuid))
        }

        test("chat history endpoint returns not found for unknown conversation") {
            val conversationUuid = UUID.fromString("bd1d0ebf-599c-4a11-a582-5a8fbb716a5c")

            whenever(chatService.getHistory(1L, conversationUuid))
                .thenThrow(ConversationNotFoundException(conversationUuid))

            mockMvc.perform(
                get("/api/v1/chat/history/$conversationUuid")
                    .principal(UsernamePasswordAuthenticationToken(1L, null))
                    .accept(MediaType.APPLICATION_JSON)
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.code").value("CONVERSATION_NOT_FOUND"))
        }

        test("chat history endpoint returns forbidden for another user's conversation") {
            val conversationUuid = UUID.fromString("7a4e58c0-e8dc-4f26-9b86-fdc50d03d49f")

            whenever(chatService.getHistory(1L, conversationUuid))
                .thenThrow(ConversationAccessDeniedException(conversationUuid))

            mockMvc.perform(
                get("/api/v1/chat/history/$conversationUuid")
                    .principal(UsernamePasswordAuthenticationToken(1L, null))
                    .accept(MediaType.APPLICATION_JSON)
            )
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.code").value("CONVERSATION_ACCESS_DENIED"))
        }

        test("chat history endpoint rejects malformed uuid") {
            mockMvc.perform(
                get("/api/v1/chat/history/not-a-uuid")
                    .principal(UsernamePasswordAuthenticationToken(1L, null))
                    .accept(MediaType.APPLICATION_JSON)
            )
                .andExpect(status().isBadRequest)

            verify(chatService, never()).getHistory(any(), any())
        }
    }

    private fun conversation(ownerId: Long, uuid: UUID): Conversation {
        val owner = User(id = ownerId, role = Role.MEMBER, provider = AuthProviderType.NONE, name = "owner")
        return Conversation(id = 1L, uuid = uuid, user = owner, title = null)
    }
}
