package com.wnl.cashchat.api.domain.chat.web.controller

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import reactor.core.publisher.Flux
import reactor.test.StepVerifier

class ChatSseEventsTest : FunSpec({

    test("appends a terminal done event carrying the reward payload on successful completion") {
        val payload = """{"pointDelta":1,"expDelta":1}"""
        StepVerifier.create(Flux.just("Hello", "!").asChatSseEvents(payload))
            .assertNext {
                it.event() shouldBe "message"
                it.data() shouldBe "Hello"
            }
            .assertNext {
                it.event() shouldBe "message"
                it.data() shouldBe "!"
            }
            .assertNext {
                it.event() shouldBe "done"
                it.data() shouldBe payload
            }
            .verifyComplete()
    }

    test("emits a single error event and no done event when the payload fails") {
        val failing = Flux.concat(Flux.just("partial"), Flux.error(RuntimeException("boom")))

        StepVerifier.create(failing.asChatSseEvents("""{"pointDelta":1,"expDelta":1}"""))
            .assertNext {
                it.event() shouldBe "message"
                it.data() shouldBe "partial"
            }
            .assertNext {
                it.event() shouldBe "error"
                it.data() shouldBe "stream failed"
            }
            .verifyComplete()
    }
})
