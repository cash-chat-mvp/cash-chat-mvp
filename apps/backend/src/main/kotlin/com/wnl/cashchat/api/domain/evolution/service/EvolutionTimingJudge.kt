package com.wnl.cashchat.api.domain.evolution.service

import com.wnl.cashchat.api.domain.evolution.exception.InvalidTimingSessionException
import com.wnl.cashchat.api.domain.evolution.properties.EvolutionProperties
import org.springframework.stereotype.Component
import kotlin.math.min

data class TimingJudgement(
    val grade: TimingGrade,
    val bonusRate: Double,
    val baseSuccessRate: Double,
    val finalSuccessRate: Double,
)

/**
 * 길게누르기 타이밍 판정. position = (releasedAtMs % cycle) / cycle 로 등급을 정하고
 * baseSuccessRate 에 보너스를 더해 최종 확률(상한 1.0)을 만든다.
 * releasedAtMs 가 세션 경과시간 + 허용오차를 넘으면 변조로 보고 거부한다.
 */
@Component
class EvolutionTimingJudge(
    private val config: EvolutionProperties.TimingConfig,
) {
    fun judge(releasedAtMs: Long, elapsedSinceStartMs: Long, baseSuccessRate: Double): TimingJudgement {
        if (releasedAtMs < 0) throw InvalidTimingSessionException("releasedAtMs must be non-negative")
        if (releasedAtMs > elapsedSinceStartMs + config.clockSkewToleranceMs) {
            throw InvalidTimingSessionException("releasedAtMs exceeds elapsed time")
        }
        val position = (releasedAtMs % config.cycleDurationMs).toDouble() / config.cycleDurationMs
        val grade = when {
            position in config.perfectStart..config.perfectEnd -> TimingGrade.PERFECT
            position in config.greatStart..config.greatEnd -> TimingGrade.GREAT
            else -> TimingGrade.NORMAL
        }
        val finalRate = min(1.0, baseSuccessRate + grade.bonusRate)
        return TimingJudgement(grade, grade.bonusRate, baseSuccessRate, finalRate)
    }
}
