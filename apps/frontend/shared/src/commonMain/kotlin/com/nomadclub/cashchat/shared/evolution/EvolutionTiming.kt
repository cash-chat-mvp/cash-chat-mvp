package com.nomadclub.cashchat.shared.evolution

enum class TimingGrade(val bonusRate: Double) {
    NORMAL(0.0),
    GREAT(0.05),
    PERFECT(0.10),
}

data class TimingWindow(
    val minimumHoldMs: Long,
    val cycleDurationMs: Long,
    val perfectStart: Float = 0.45f,
    val perfectEnd: Float = 0.55f,
    val greatStart: Float = 0.38f,
    val greatEnd: Float = 0.62f,
)

fun localTimingGrade(position: Float, window: TimingWindow): TimingGrade = when {
    position in window.perfectStart..window.perfectEnd -> TimingGrade.PERFECT
    position in window.greatStart..window.greatEnd -> TimingGrade.GREAT
    else -> TimingGrade.NORMAL
}
