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

    test("consuming another user's session is rejected and does not destroy the owner's session") {
        val store = TimingSessionStore(config)
        val session = store.issue(userId = 1L)
        shouldThrow<InvalidTimingSessionException> {
            store.consume(session.sessionId, userId = 2L, now = session.serverStartedAt.plusSeconds(1))
        }
        // 오너 불일치 요청이 세션을 소모해선 안 된다 — 원래 사용자(1L)는 여전히 consume 할 수 있어야 한다.
        val consumed = store.consume(session.sessionId, userId = 1L, now = session.serverStartedAt.plusSeconds(1))
        consumed.sessionId shouldBe session.sessionId
    }

    test("expired unconsumed sessions are swept on next issue (no leak)") {
        val fastConfig = config.copy(sessionTtl = Duration.ofMillis(1))
        val store = TimingSessionStore(fastConfig)
        val stale = store.issue(userId = 1L)
        Thread.sleep(10)
        store.issue(userId = 2L) // 발급 시 만료분 스윕

        // now 를 만료 전으로 줘도 'Unknown'이 떠야 스윕으로 제거됐음이 증명된다(아직 있었다면 성공했을 것).
        val ex = shouldThrow<InvalidTimingSessionException> {
            store.consume(stale.sessionId, userId = 1L, now = stale.serverStartedAt)
        }
        ex.message shouldBe "Unknown timing session"
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
