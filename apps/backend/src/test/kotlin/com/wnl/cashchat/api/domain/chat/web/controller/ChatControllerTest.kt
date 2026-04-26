package com.wnl.cashchat.api.domain.chat.web.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.wnl.cashchat.api.common.security.jwt.JwtTokenHandler
import com.wnl.cashchat.api.domain.chat.service.ChatService
import com.wnl.cashchat.api.domain.chat.web.request.ChatStreamRequest
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter
import reactor.core.publisher.Flux
import java.util.concurrent.atomic.AtomicBoolean

@WebMvcTest(ChatController::class)
@AutoConfigureMockMvc(addFilters = false)
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
        test("chat stream endpoint returns text event stream for authenticated user") {
            whenever(chatService.stream(1L, 7L, "hello")).thenReturn(Flux.just("hi there"))

            val result = mockMvc.perform(
                post("/api/v1/chat/stream")
                    .principal(UsernamePasswordAuthenticationToken(1L, null))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .content(objectMapper.writeValueAsString(mapOf("conversationId" to 7L, "message" to "hello")))
            )
                .andExpect(request().asyncStarted())
                .andReturn()

            mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk)
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))

            verify(chatService).stream(eq(1L), eq(7L), eq("hello"))
        }

        test("chat stream endpoint rejects a missing conversation id") {
            mockMvc.perform(
                post("/api/v1/chat/stream")
                    .principal(UsernamePasswordAuthenticationToken(1L, null))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(mapOf("message" to "hello")))
            )
                .andExpect(status().isBadRequest)
        }

        test("chat stream endpoint rejects a blank message") {
            mockMvc.perform(
                post("/api/v1/chat/stream")
                    .principal(UsernamePasswordAuthenticationToken(1L, null))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(mapOf("conversationId" to 7L, "message" to "")))
            )
                .andExpect(status().isBadRequest)
        }

        test("chat stream endpoint sends a generic error message") {
            whenever(chatService.stream(1L, 7L, "hello")).thenReturn(Flux.error(IllegalStateException("sensitive details")))

            val result = mockMvc.perform(
                post("/api/v1/chat/stream")
                    .principal(UsernamePasswordAuthenticationToken(1L, null))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .content(objectMapper.writeValueAsString(mapOf("conversationId" to 7L, "message" to "hello")))
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
    }
}

class ChatControllerLifecycleTest : FunSpec() {
    private lateinit var chatService: ChatService
    private lateinit var controller: ChatController

    init {
        beforeTest {
            chatService = mock()
            controller = ChatController(chatService)
        }

        test("stream disposes the upstream subscription when the emitter completes") {
            val disposed = AtomicBoolean(false)
            whenever(chatService.stream(1L, 7L, "hello")).thenReturn(
                Flux.create<String> { sink -> sink.onDispose { disposed.set(true) } }
            )

            val emitter = controller.stream(
                authentication = UsernamePasswordAuthenticationToken(1L, null),
                request = ChatStreamRequest(conversationId = 7L, message = "hello")
            )

            invokeCallback(emitter, "completionCallback", "run")

            disposed.get() shouldBe true
        }

        test("stream disposes the upstream subscription when the emitter errors") {
            val disposed = AtomicBoolean(false)
            whenever(chatService.stream(1L, 7L, "hello")).thenReturn(
                Flux.create<String> { sink -> sink.onDispose { disposed.set(true) } }
            )

            val emitter = controller.stream(
                authentication = UsernamePasswordAuthenticationToken(1L, null),
                request = ChatStreamRequest(conversationId = 7L, message = "hello")
            )

            invokeCallback(emitter, "errorCallback", "accept", IllegalStateException("client disconnected"))

            disposed.get() shouldBe true
        }
    }

    private fun invokeCallback(emitter: ResponseBodyEmitter, fieldName: String, methodName: String, argument: Any? = null) {
        val field = ResponseBodyEmitter::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        val callback = field.get(emitter)
        val method = callback.javaClass.methods.first { candidate ->
            candidate.name == methodName && candidate.parameterCount == if (argument == null) 0 else 1
        }
        method.isAccessible = true

        if (argument == null) {
            method.invoke(callback)
        } else {
            method.invoke(callback, argument)
        }
    }
}
