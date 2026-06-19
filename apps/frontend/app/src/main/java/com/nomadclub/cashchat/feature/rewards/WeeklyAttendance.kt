package com.nomadclub.cashchat.feature.rewards

import java.util.Calendar

/** 출석 주간 뷰의 한 칸. */
data class AttendanceDayCell(
    val dayOfMonth: Int,
    val inDisplayedMonth: Boolean,
    val checked: Boolean,
    val isToday: Boolean,
)

/**
 * 오늘이 포함된 주(일요일~토요일) 7칸을 계산한다.
 * 표시 월(displayedYear/Month)에 속하는 칸만 checkedDays 로 완료 판정하고,
 * 다른 달 칸은 inDisplayedMonth=false(중립)로 둔다.
 * java.time 미사용(desugaring 미설정으로 런타임 크래시 회피) — java.util.Calendar 사용.
 */
fun weeklyAttendanceCells(
    displayedYear: Int,
    displayedMonth: Int,
    todayYear: Int,
    todayMonth: Int,
    todayDay: Int,
    checkedDays: Set<Int>,
): List<AttendanceDayCell> {
    val cal = Calendar.getInstance()
    cal.clear()
    cal.set(todayYear, todayMonth - 1, todayDay)
    val offset = cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
    cal.add(Calendar.DAY_OF_MONTH, -offset)
    return (0 until 7).map {
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        val inMonth = y == displayedYear && m == displayedMonth
        val cell = AttendanceDayCell(
            dayOfMonth = d,
            inDisplayedMonth = inMonth,
            checked = inMonth && checkedDays.contains(d),
            isToday = y == todayYear && m == todayMonth && d == todayDay,
        )
        cal.add(Calendar.DAY_OF_MONTH, 1)
        cell
    }
}
