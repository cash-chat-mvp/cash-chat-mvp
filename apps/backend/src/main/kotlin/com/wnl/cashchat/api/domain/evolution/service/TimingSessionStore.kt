package com.wnl.cashchat.api.domain.evolution.service

import com.wnl.cashchat.api.domain.evolution.exception.InvalidTimingSessionException
import com.wnl.cashchat.api.domain.evolution.properties.EvolutionProperties
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class TimingSession(
    val sessionId: String,
    val userId: Long,
    val serverStartedAt: Instant,
    val expiresAt: Instant,
)

/**
 * 인메모리 1회용 타이밍 세션 스토어. 단일 인스턴스 배포 전제.
 * consume 은 같은 사용자·미만료일 때만 성공하고 즉시 제거한다(중복 사용 차단).
 */
@Component
class TimingSessionStore(
    private val config: EvolutionProperties.TimingConfig,
) {
    private val sessions = ConcurrentHashMap<String, TimingSession>()

    fun issue(userId: Long): TimingSession {
        val now = Instant.now()
        val session = TimingSession(
            sessionId = UUID.randomUUID().toString(),
            userId = userId,
            serverStartedAt = now,
            expiresAt = now.plus(config.sessionTtl),
        )
        sessions[session.sessionId] = session
        return session
    }

    fun consume(sessionId: String, userId: Long, now: Instant): TimingSession {
        val session = sessions.remove(sessionId)
            ?: throw InvalidTimingSessionException("Unknown timing session")
        if (session.userId != userId) throw InvalidTimingSessionException("Timing session owner mismatch")
        if (now.isAfter(session.expiresAt)) throw InvalidTimingSessionException("Timing session expired")
        return session
    }
}
