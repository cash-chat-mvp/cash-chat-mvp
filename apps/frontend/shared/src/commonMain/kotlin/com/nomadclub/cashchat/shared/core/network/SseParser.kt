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
                // "data:" 뒤 내용을 그대로 누적한다. 백엔드(Spring ServerSentEvent)는 `data:`에 SSE
                // 표준의 선택적 framing 공백을 붙이지 않고 토큰을 그대로 내보내므로(예: `data: 좋은`의
                // 공백은 단어 경계인 콘텐츠 공백), 여기서 선행 공백을 제거하면 단어 사이 띄어쓰기가 사라진다.
                dataLines.add(line.removePrefix("data:"))
                null
            }
            line.isBlank() -> takeCompleted()
            else -> null // comment 등 무시
        }
    }

    /**
     * 스트림 종료(EOF) 시 호출. 마지막 이벤트가 종결 빈 줄 없이 끊긴 경우, 누적된 data 라인을
     * 마지막 이벤트로 반환한다. 없으면 null. 이걸 호출하지 않으면 종결 빈 줄이 도착하기 전에
     * 연결이 끊긴 스트림의 마지막 토큰이 유실된다.
     */
    fun flush(): SseEvent? = takeCompleted()

    private fun takeCompleted(): SseEvent? {
        val completed = if (dataLines.isEmpty()) null
        else SseEvent(eventType ?: "message", dataLines.joinToString("\n"))
        eventType = null
        dataLines.clear()
        return completed
    }
}
