package com.wnl.cashchat.api.domain.attendance.web.controller

import com.wnl.cashchat.api.domain.attendance.exception.InvalidAttendanceQueryException
import com.wnl.cashchat.api.domain.attendance.service.AttendanceService
import com.wnl.cashchat.api.domain.attendance.web.response.CheckInResponse
import com.wnl.cashchat.api.domain.attendance.web.response.MonthlyAttendanceResponse
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
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

    // 인증된 요청은 JwtAuthenticationFilter 가 principal 에 Long userId 를 세팅한다(도달 가능성은 낮은 방어 경로).
    // principal 이 Long 이 아니면 인증 문제이므로 401 흐름을 타도록 AuthenticationException 을 던진다(500 아님).
    private fun Authentication.userId(): Long =
        principal as? Long ?: throw AuthenticationCredentialsNotFoundException("Invalid authenticated principal")

    private companion object {
        private val KST = ZoneId.of("Asia/Seoul")
    }
}
