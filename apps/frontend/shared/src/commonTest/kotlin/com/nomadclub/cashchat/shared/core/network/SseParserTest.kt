package com.nomadclub.cashchat.shared.core.network

import kotlin.test.Test
import kotlin.test.assertEquals

class SseParserTest {

    private fun parseAll(raw: String): List<SseEvent> {
        val parser = SseParser()
        return raw.lines().mapNotNull(parser::feed)
    }

    // 백엔드(Spring ServerSentEvent)는 `data:` 뒤에 framing 공백 없이 토큰을 그대로 내보낸다.
    // 따라서 테스트 입력도 실제 와이어 포맷(`data:` + 콘텐츠 그대로)을 따른다.

    @Test
    fun `message 이벤트의 data를 토큰으로 반환한다`() {
        val events = parseAll("event:message\ndata:안녕\n\nevent:message\ndata:하세요\n\n")
        assertEquals(listOf(SseEvent("message", "안녕"), SseEvent("message", "하세요")), events)
    }

    @Test
    fun `data의 선행 공백을 보존한다 - 단어 경계 유지`() {
        // 백엔드가 단어 시작 토큰을 `data: 좋은`처럼 콘텐츠 공백과 함께 보낸다. 이 공백을 제거하면
        // "가성비좋은"처럼 띄어쓰기가 사라지므로, 토큰을 그대로 누적해 단어 경계를 보존해야 한다.
        val events = parseAll("event:message\ndata:가\n\nevent:message\ndata: 좋은\n\n")
        assertEquals(listOf(SseEvent("message", "가"), SseEvent("message", " 좋은")), events)
    }

    @Test
    fun `error 이벤트를 그대로 반환한다`() {
        val events = parseAll("event:error\ndata:stream failed\n\n")
        assertEquals(listOf(SseEvent("error", "stream failed")), events)
    }

    @Test
    fun `event 라인이 없으면 기본 message 타입으로 처리한다`() {
        val events = parseAll("data:hello\n\n")
        assertEquals(listOf(SseEvent("message", "hello")), events)
    }

    @Test
    fun `빈 data는 빈 문자열 토큰으로 유지한다`() {
        val events = parseAll("event:message\ndata:\n\n")
        assertEquals(listOf(SseEvent("message", "")), events)
    }

    @Test
    fun `여러 data 라인은 줄바꿈으로 이어 붙인다`() {
        // SSE 표준: data에 \n이 포함되면 여러 data 라인으로 인코딩되고, 수신 측은 \n으로 재결합한다.
        // (예: LLM이 "추천해드릴게요.\n\n1. 비빔밥" 청크를 보내는 경우)
        val events = parseAll("event:message\ndata:추천해드릴게요.\ndata:\ndata:1. 비빔밥\n\n")
        assertEquals(listOf(SseEvent("message", "추천해드릴게요.\n\n1. 비빔밥")), events)
    }

    @Test
    fun `종결 빈 줄 없이 끊긴 마지막 이벤트를 flush로 회수한다`() {
        // 연결이 종결 빈 줄 전에 끊긴 경우 feed만으로는 마지막 토큰이 유실된다.
        val parser = SseParser()
        val duringStream = listOf("event:message", "data:마지막").mapNotNull(parser::feed)
        assertEquals(emptyList(), duringStream)
        assertEquals(SseEvent("message", "마지막"), parser.flush())
    }

    @Test
    fun `정상 종결된 이후 flush는 null`() {
        // data가 빈 줄로 이미 방출된 뒤에는 flush할 잔여 이벤트가 없다.
        val parser = SseParser()
        parser.feed("data:x")
        parser.feed("")
        assertEquals(null, parser.flush())
    }
}
