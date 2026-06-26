package com.wnl.cashchat.api.domain.evolution.service

import com.wnl.cashchat.api.domain.evolution.exception.InvalidTimingSessionException
import com.wnl.cashchat.api.domain.evolution.properties.EvolutionProperties
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.Instant

class TimingSessionStoreTest : FunSpec({
    val config = EvolutionProperties().timing // sessionTtl 2m

    test("issued session can be consumed once by the same user") {
        val store = TimingSessionStore(config)
        val session = store.issue(userId = 1L)

        val consumed = store.consume(session.sessionId, userId = 1L, now = session.serverStartedAt.plusSeconds(1))
        consumed.sessionId shouldBe session.sessionId

        shouldThrow<InvalidTimingSessionException> {
            store.consume(session.sessionId, userId = 1L, now = session.serverStartedAt.plusSeconds(1))
        }
    }

    test("consuming another user's session is rejected") {
        val store = TimingSessionStore(config)
        val session = store.issue(userId = 1L)
        shouldThrow<InvalidTimingSessionException> {
            store.consume(session.sessionId, userId = 2L, now = session.serverStartedAt.plusSeconds(1))
        }
    }

    test("expired session is rejected") {
        val store = TimingSessionStore(config)
        val session = store.issue(userId = 1L)
        val afterTtl = session.serverStartedAt.plus(Duration.ofMinutes(5))
        shouldThrow<InvalidTimingSessionException> {
            store.consume(session.sessionId, userId = 1L, now = afterTtl)
        }
    }

    test("unknown session id is rejected") {
        val store = TimingSessionStore(config)
        shouldThrow<InvalidTimingSessionException> {
            store.consume("nope", userId = 1L, now = Instant.now())
        }
    }
})
