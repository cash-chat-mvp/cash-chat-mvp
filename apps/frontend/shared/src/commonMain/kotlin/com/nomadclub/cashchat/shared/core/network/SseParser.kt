package com.nomadclub.cashchat.shared.core.network

/** 파싱된 SSE 이벤트 한 건. [event]는 "message" 또는 "error". */
data class SseEvent(val event: String, val data: String)

/**
 * SSE 텍스트 스트림을 라인 단위로 받아 이벤트를 조립한다.
 * 빈 줄이 이벤트 경계. event 라인이 없으면 SSE 표준대로 "message"로 간주.
 */
class SseParser {
    private var eventType: String? = null
    private var data: String? = null

    /** 라인 1개를 소비하고, 이벤트가 완성되면 반환(아니면 null). */
    fun feed(line: String): SseEvent? {
        return when {
            line.startsWith("event:") -> {
                eventType = line.removePrefix("event:").trim()
                null
            }
            line.startsWith("data:") -> {
                data = line.removePrefix("data:").removePrefix(" ")
                null
            }
            line.isBlank() -> {
                val completed = data?.let { SseEvent(eventType ?: "message", it) }
                eventType = null
                data = null
                completed
            }
            else -> null // comment 등 무시
        }
    }
}
