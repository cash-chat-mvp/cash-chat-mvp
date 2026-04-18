package com.wnl.cashchat.api.domain.chat.web.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.wnl.cashchat.api.domain.chat.service.ChatService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import reactor.core.publisher.Flux

class ChatControllerTest {

    private val objectMapper = ObjectMapper()

    private lateinit var chatService: ChatService
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        chatService = mock {
            on { stream(any(), any(), any()) } doReturn Flux.just("hi there")
        }

        mockMvc = MockMvcBuilders.standaloneSetup(ChatController(chatService))
            .setMessageConverters(MappingJackson2HttpMessageConverter(objectMapper))
            .build()
    }

    @Test
    fun `chat stream endpoint returns text event stream for authenticated user`() {
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
}
