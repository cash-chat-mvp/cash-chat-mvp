package com.nomadclub.cashchat.feature.rewards

import org.junit.Assert.assertEquals
import org.junit.Test

class WeeklyAttendanceTest {

    // 2026-05-20은 수요일 → 그 주(일~토)는 5/17~5/23
    @Test
    fun `이번 주 7칸은 일요일부터 토요일까지이며 오늘과 완료를 표시한다`() {
        val cells = weeklyAttendanceCells(
            displayedYear = 2026, displayedMonth = 5,
            todayYear = 2026, todayMonth = 5, todayDay = 20,
            checkedDays = setOf(17, 18, 19, 20),
        )
        assertEquals(listOf(17, 18, 19, 20, 21, 22, 23), cells.map { it.dayOfMonth })
        assertEquals(List(7) { true }, cells.map { it.inDisplayedMonth })
        assertEquals(listOf(true, true, true, true, false, false, false), cells.map { it.checked })
        assertEquals(listOf(false, false, false, true, false, false, false), cells.map { it.isToday })
    }

    // 2026-05-01은 금요일 → 그 주는 4/26~5/2 (월 경계)
    @Test
    fun `월 경계 주에서는 다른 달 칸을 중립으로 표시한다`() {
        val cells = weeklyAttendanceCells(
            displayedYear = 2026, displayedMonth = 5,
            todayYear = 2026, todayMonth = 5, todayDay = 1,
            checkedDays = setOf(1),
        )
        assertEquals(listOf(26, 27, 28, 29, 30, 1, 2), cells.map { it.dayOfMonth })
        assertEquals(listOf(false, false, false, false, false, true, true), cells.map { it.inDisplayedMonth })
        assertEquals(listOf(false, false, false, false, false, true, false), cells.map { it.checked })
        assertEquals(listOf(false, false, false, false, false, true, false), cells.map { it.isToday })
    }
}
