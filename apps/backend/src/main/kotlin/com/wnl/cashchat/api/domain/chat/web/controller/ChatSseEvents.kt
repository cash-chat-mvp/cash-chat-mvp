package com.wnl.cashchat.api.domain.chat.web.controller

import org.springframework.http.codec.ServerSentEvent
import reactor.core.publisher.Flux

internal const val MESSAGE_EVENT = "message"
internal const val DONE_EVENT = "done"
internal const val ERROR_EVENT = "error"
internal const val STREAM_DONE_DATA = "[DONE]"
internal const val STREAM_FAILED_MESSAGE = "stream failed"

/**
 * Maps a raw chat token stream into the SSE event protocol the client consumes.
 *
 * On successful completion a terminal `event: done` (data `[DONE]`) is appended so the client
 * can treat end-of-stream as application data and stop reading. This makes the stream resilient
 * to the transport-level `RST_STREAM(INTERNAL_ERROR)` that nginx emits over HTTP/2 right after
 * the upstream SSE response closes: the client has already seen `done` and ignores the reset
 * instead of surfacing it as a "connection dropped" error.
 *
 * On failure the upstream error is surfaced as a single `event: error` and no `done` is sent,
 * so clients can distinguish a clean finish from a real failure.
 */
internal fun Flux<String>.asChatSseEvents(): Flux<ServerSentEvent<String>> =
    map { chunk -> ServerSentEvent.builder<String>(chunk).event(MESSAGE_EVENT).build() }
        .concatWith(Flux.just(ServerSentEvent.builder<String>(STREAM_DONE_DATA).event(DONE_EVENT).build()))
        .onErrorResume {
            Flux.just(ServerSentEvent.builder<String>(STREAM_FAILED_MESSAGE).event(ERROR_EVENT).build())
        }
