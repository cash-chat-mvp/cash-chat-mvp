package com.wnl.cashchat.api.domain.attendance.web.controller

import com.wnl.cashchat.api.domain.attendance.exception.InvalidAttendanceQueryException
import com.wnl.cashchat.api.domain.attendance.service.AttendanceService
import com.wnl.cashchat.api.domain.attendance.web.response.CheckInResponse
import com.wnl.cashchat.api.domain.attendance.web.response.MonthlyAttendanceResponse
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.ZoneId

@RestController
@RequestMapping("/api/attendance")
class AttendanceController(
    private val attendanceService: AttendanceService,
) {
    @PostMapping("/check-in")
    fun checkIn(authentication: Authentication): CheckInResponse =
        CheckInResponse.from(
            attendanceService.checkIn(authentication.userId(), LocalDate.now(KST))
        )

    @GetMapping("/me")
    fun getMonthly(
        authentication: Authentication,
        @RequestParam(required = false) year: Int?,
        @RequestParam(required = false) month: Int?,
    ): MonthlyAttendanceResponse {
        val today = LocalDate.now(KST)
        if ((year == null) != (month == null)) {
            throw InvalidAttendanceQueryException("year and month must be provided together")
        }
        val resolvedYear = year ?: today.year
        val resolvedMonth = month ?: today.monthValue
        if (resolvedMonth !in 1..12) {
            throw InvalidAttendanceQueryException("month must be between 1 and 12")
        }
        return MonthlyAttendanceResponse.from(
            attendanceService.getMonthly(authentication.userId(), resolvedYear, resolvedMonth, today)
        )
    }

    private fun Authentication.userId(): Long =
        principal as? Long ?: throw IllegalArgumentException("Invalid authenticated principal")

    private companion object {
        private val KST = ZoneId.of("Asia/Seoul")
    }
}
