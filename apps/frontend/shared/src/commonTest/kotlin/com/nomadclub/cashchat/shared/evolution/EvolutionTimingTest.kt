package com.nomadclub.cashchat.shared.evolution

import kotlin.test.Test
import kotlin.test.assertEquals

class EvolutionTimingTest {
    private val window = TimingWindow(
        minimumHoldMs = 600,
        cycleDurationMs = 1800,
        perfectStart = 0.45f,
        perfectEnd = 0.55f,
        greatStart = 0.38f,
        greatEnd = 0.62f,
    )

    @Test
    fun `center is perfect`() {
        assertEquals(TimingGrade.PERFECT, localTimingGrade(0.50f, window))
    }

    @Test
    fun `great excludes perfect`() {
        assertEquals(TimingGrade.GREAT, localTimingGrade(0.40f, window))
    }

    @Test
    fun `outside bonus windows is normal`() {
        assertEquals(TimingGrade.NORMAL, localTimingGrade(0.20f, window))
    }
}
