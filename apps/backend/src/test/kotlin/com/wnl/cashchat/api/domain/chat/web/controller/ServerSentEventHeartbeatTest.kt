package com.wnl.cashchat.api.domain.chat.web.controller

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.springframework.http.codec.ServerSentEvent
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.time.Duration

class ServerSentEventHeartbeatTest : FunSpec({

    fun dataEvent(value: String): ServerSentEvent<String> =
        ServerSentEvent.builder<String>(value).event("message").build()

    test("interleaves heartbeat comments while the payload is idle and stops once it completes") {
        StepVerifier.withVirtualTime {
            // A single data event that only arrives after 35s of silence.
            Flux.just(dataEvent("done"))
                .delayElements(Duration.ofSeconds(35))
                .withHeartbeat(Duration.ofSeconds(15))
        }
            .thenAwait(Duration.ofSeconds(15))
            .assertNext { it.comment() shouldBe "keep-alive" }
            .thenAwait(Duration.ofSeconds(15))
            .assertNext { it.comment() shouldBe "keep-alive" }
            .thenAwait(Duration.ofSeconds(5))
            .assertNext { it.data() shouldBe "done" }
            .verifyComplete()
    }

    test("does not emit a heartbeat when the payload completes before the first interval") {
        StepVerifier.withVirtualTime {
            Flux.just(dataEvent("hi")).withHeartbeat(Duration.ofSeconds(15))
        }
            .assertNext { it.data() shouldBe "hi" }
            .verifyComplete()
    }
})
