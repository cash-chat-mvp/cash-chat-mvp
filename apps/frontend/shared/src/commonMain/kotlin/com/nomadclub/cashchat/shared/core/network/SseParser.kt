package com.nomadclub.cashchat.shared.core.network

/** 파싱된 SSE 이벤트 한 건. [event]는 "message" 또는 "error". */
data class SseEvent(val event: String, val data: String)

/**
 * SSE 텍스트 스트림을 라인 단위로 받아 이벤트를 조립한다.
 * 빈 줄이 이벤트 경계. event 라인이 없으면 SSE 표준대로 "message"로 간주.
 */
class SseParser {
    private var eventType: String? = null
    // SSE 표준: 한 이벤트에 data 라인이 여러 개면 \n으로 이어 붙인다. data에 줄바꿈이 든
    // 경우(예: LLM 마크다운 응답) 여러 data 라인으로 인코딩되므로 덮어쓰지 않고 누적해야 한다.
    private val dataLines = mutableListOf<String>()

    /** 라인 1개를 소비하고, 이벤트가 완성되면 반환(아니면 null). */
    fun feed(line: String): SseEvent? {
        return when {
            line.startsWith("event:") -> {
                eventType = line.removePrefix("event:").trim()
                null
            }
            line.startsWith("data:") -> {
                dataLines.add(line.removePrefix("data:").removePrefix(" "))
                null
            }
            line.isBlank() -> {
                val completed = if (dataLines.isEmpty()) null
                else SseEvent(eventType ?: "message", dataLines.joinToString("\n"))
                eventType = null
                dataLines.clear()
                completed
            }
            else -> null // comment 등 무시
        }
    }
}
