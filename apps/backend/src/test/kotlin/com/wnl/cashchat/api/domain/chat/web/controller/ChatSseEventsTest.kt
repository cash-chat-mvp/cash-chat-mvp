package com.wnl.cashchat.api.domain.chat.web.controller

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import reactor.core.publisher.Flux
import reactor.test.StepVerifier

class ChatSseEventsTest : FunSpec({

    test("appends a terminal done event after the payload on successful completion") {
        StepVerifier.create(Flux.just("Hello", "!").asChatSseEvents())
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
                it.data() shouldBe "[DONE]"
            }
            .verifyComplete()
    }

    test("emits a single error event and no done event when the payload fails") {
        val failing = Flux.concat(Flux.just("partial"), Flux.error(RuntimeException("boom")))

        StepVerifier.create(failing.asChatSseEvents())
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
