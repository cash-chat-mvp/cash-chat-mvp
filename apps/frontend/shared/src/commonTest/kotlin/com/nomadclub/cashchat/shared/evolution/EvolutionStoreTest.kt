package com.nomadclub.cashchat.shared.evolution

import com.nomadclub.cashchat.shared.core.network.ApiException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private class FakeEvolutionGateway(
    var session: TimingSessionDto? = null,
    var timingError: Throwable? = null,
    private val attemptResult: EvolutionAttemptDto = EvolutionAttemptDto(
        success = true,
        fromLevel = 2,
        resultLevel = 3,
        cost = 1200,
    ),
) : EvolutionGateway {
    var createTimingSessionCalls = 0
    var onAttempt: (String, TimingAttempt?) -> EvolutionAttemptDto = { _, _ -> attemptResult }
    val attemptKeys = mutableListOf<String>()
    val attemptTimings = mutableListOf<TimingAttempt?>()

    override suspend fun getState(): EvolutionStateDto =
        EvolutionStateDto(level = 1, isMaxLevel = false)

    override suspend fun createTimingSession(): TimingSessionDto {
        createTimingSessionCalls++
        timingError?.let { throw it }
        return requireNotNull(session)
    }

    override suspend fun attempt(idempotencyKey: String, timing: TimingAttempt?): EvolutionAttemptDto {
        attemptKeys += idempotencyKey
        attemptTimings += timing
        return onAttempt(idempotencyKey, timing)
    }

    override suspend fun getAttempts(limit: Int): EvolutionAttemptsDto =
        EvolutionAttemptsDto(emptyList())
}

class EvolutionStoreTest {

    private val session = TimingSessionDto(
        sessionId = "s1",
        serverStartedAt = "2026-06-26T00:00:00Z",
        minimumHoldMs = 600,
        cycleDurationMs = 1800,
    )

    @Test
    fun `404 timing session falls back to unsupported`() = runTest {
        val gateway = FakeEvolutionGateway(
            timingError = ApiException("NOT_FOUND", "missing", 404),
        )
        val store = EvolutionStore.fromGateway(gateway)

        assertEquals(TimingCapability.UNSUPPORTED, store.detectTimingCapability())
        assertEquals(1, gateway.createTimingSessionCalls)
    }

    @Test
    fun `supported detection exposes timing session and reset clears it`() = runTest {
        val gateway = FakeEvolutionGateway(session = session)
        val store = EvolutionStore.fromGateway(gateway)

        store.detectTimingCapability()
        assertEquals(session, store.timingSession.value)

        store.reset()
        assertEquals(null, store.timingSession.value)
        assertEquals(TimingCapability.UNKNOWN, store.timingCapability.value)
    }

    @Test
    fun `timing attempt sends session data`() = runTest {
        val gateway = FakeEvolutionGateway(session = session)
        val store = EvolutionStore.fromGateway(gateway)

        store.detectTimingCapability()
        store.attempt(TimingAttempt("s1", 1432))

        assertEquals("s1", gateway.attemptTimings.single()?.sessionId)
        assertEquals(1432L, gateway.attemptTimings.single()?.releasedAtMs)
    }

    @Test
    fun `retry keeps same idempotency key and timing payload after timing failure`() = runTest {
        val gateway = FakeEvolutionGateway(session = session)
        val store = EvolutionStore.fromGateway(gateway)
        val timing = TimingAttempt("s1", 1432)
        var calls = 0
        gateway.onAttempt = { _, _ ->
            calls++
            if (calls == 1) throw IllegalStateException("timeout")
            EvolutionAttemptDto(success = true, fromLevel = 2, resultLevel = 3, cost = 1200)
        }

        store.detectTimingCapability()

        assertFailsWith<IllegalStateException> {
            store.attempt(timing)
        }

        store.retryLastAttempt()

        assertEquals(2, gateway.attemptKeys.size)
        assertEquals(gateway.attemptKeys[0], gateway.attemptKeys[1])
        assertEquals(timing, gateway.attemptTimings[0])
        assertEquals(timing, gateway.attemptTimings[1])
    }

    @Test
    fun `detect failure disables timing bonus and does not forward stale session attempt`() = runTest {
        val gateway = FakeEvolutionGateway(session = session)
        val store = EvolutionStore.fromGateway(gateway)
        val staleTiming = TimingAttempt("s1", 1432)

        assertEquals(TimingCapability.SUPPORTED, store.detectTimingCapability())

        gateway.timingError = ApiException("NOT_FOUND", "missing", 404)

        assertEquals(TimingCapability.UNSUPPORTED, store.detectTimingCapability())

        store.attempt(staleTiming)

        assertEquals(null, gateway.attemptTimings.single())
    }
}
