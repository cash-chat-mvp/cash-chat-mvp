package com.nomadclub.cashchat.shared.core.network

import kotlin.test.Test
import kotlin.test.assertEquals

class SseParserTest {

    private fun parseAll(raw: String): List<SseEvent> {
        val parser = SseParser()
        return raw.lines().mapNotNull(parser::feed)
    }

    @Test
    fun `message 이벤트의 data를 토큰으로 반환한다`() {
        val events = parseAll("event: message\ndata: 안녕\n\nevent: message\ndata: 하세요\n\n")
        assertEquals(listOf(SseEvent("message", "안녕"), SseEvent("message", "하세요")), events)
    }

    @Test
    fun `error 이벤트를 그대로 반환한다`() {
        val events = parseAll("event: error\ndata: stream failed\n\n")
        assertEquals(listOf(SseEvent("error", "stream failed")), events)
    }

    @Test
    fun `event 라인이 없으면 기본 message 타입으로 처리한다`() {
        val events = parseAll("data: hello\n\n")
        assertEquals(listOf(SseEvent("message", "hello")), events)
    }

    @Test
    fun `data에 콜론 공백 없이 와도 파싱한다`() {
        val events = parseAll("event:message\ndata:hi\n\n")
        assertEquals(listOf(SseEvent("message", "hi")), events)
    }

    @Test
    fun `빈 data는 빈 문자열 토큰으로 유지한다`() {
        val events = parseAll("event: message\ndata: \n\n")
        assertEquals(listOf(SseEvent("message", "")), events)
    }

    @Test
    fun `여러 data 라인은 줄바꿈으로 이어 붙인다`() {
        // SSE 표준: data에 \n이 포함되면 여러 data 라인으로 인코딩되고, 수신 측은 \n으로 재결합한다.
        // (예: LLM이 "추천해드릴게요.\n\n1. 비빔밥" 청크를 보내는 경우)
        val events = parseAll("event: message\ndata: 추천해드릴게요.\ndata:\ndata: 1. 비빔밥\n\n")
        assertEquals(listOf(SseEvent("message", "추천해드릴게요.\n\n1. 비빔밥")), events)
    }
}
