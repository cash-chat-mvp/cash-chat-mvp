package com.wnl.cashchat.api.domain.chat.web.controller

import org.springframework.http.codec.ServerSentEvent
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.time.Duration

/**
 * Interleaves periodic SSE comment "heartbeats" into this payload stream.
 *
 * A heartbeat is an SSE comment line (`:keep-alive`) that clients ignore but that keeps the
 * connection alive across nginx, carrier NATs and intermediate proxies during idle gaps —
 * e.g. a slow LLM time-to-first-token. The heartbeat stops as soon as the payload terminates,
 * so the merged stream completes (or errors) together with the payload.
 *
 * @param interval gap between heartbeats; keep it well below the proxy read timeout.
 */
internal fun Flux<ServerSentEvent<String>>.withHeartbeat(
    interval: Duration,
    heartbeat: () -> ServerSentEvent<String> = {
        ServerSentEvent.builder<String>().comment("keep-alive").build()
    },
): Flux<ServerSentEvent<String>> {
    val payloadFinished = Sinks.empty<Void>()
    val payload = doFinally { payloadFinished.tryEmitEmpty() }
    val heartbeats = Flux.interval(interval)
        .map { heartbeat() }
        .takeUntilOther(payloadFinished.asMono())
    return Flux.merge(payload, heartbeats)
}
