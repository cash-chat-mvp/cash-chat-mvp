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
        // 미소비/만료 세션 정리(누수 방지): consume 되지 않은 세션은 만료 후에도 남으므로 발급 시 한 번 쓸어낸다.
        sessions.values.removeIf { now.isAfter(it.expiresAt) }
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
        // 검증 통과 시에만 원자적으로 제거한다. 오너 불일치·만료·미존재면 throw 하여 맵을 그대로 둔다
        // (compute 의 remapping 이 예외를 던지면 매핑은 변경되지 않는다) — 잘못된 요청이 정상 세션을 소모하지 못하게 한다.
        var validated: TimingSession? = null
        sessions.compute(sessionId) { _, existing ->
            if (existing == null) throw InvalidTimingSessionException("Unknown timing session")
            if (existing.userId != userId) throw InvalidTimingSessionException("Timing session owner mismatch")
            if (now.isAfter(existing.expiresAt)) throw InvalidTimingSessionException("Timing session expired")
            validated = existing
            null // 검증 통과 → 1회용으로 원자적 제거
        }
        return validated!!
    }
}
