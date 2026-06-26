package com.wnl.cashchat.api.domain.evolution.web.response

import com.wnl.cashchat.api.domain.evolution.properties.EvolutionProperties
import com.wnl.cashchat.api.domain.evolution.service.TimingSession
import java.time.Instant

data class TimingSessionResponse(
    val sessionId: String,
    val serverStartedAt: Instant,
    val minimumHoldMs: Long,
    val cycleDurationMs: Long,
) {
    companion object {
        fun from(session: TimingSession, config: EvolutionProperties.TimingConfig) = TimingSessionResponse(
            sessionId = session.sessionId,
            serverStartedAt = session.serverStartedAt,
            minimumHoldMs = config.minimumHoldMs,
            cycleDurationMs = config.cycleDurationMs,
        )
    }
}
