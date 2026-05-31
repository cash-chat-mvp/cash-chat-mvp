package com.wnl.cashchat.api.domain.attendance.persistence.repository

import com.wnl.cashchat.api.domain.attendance.persistence.entity.AttendanceLog
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface AttendanceLogRepository : JpaRepository<AttendanceLog, Long> {
    fun existsByUserIdAndCheckInDate(userId: Long, checkInDate: LocalDate): Boolean

    fun findTopByUserIdOrderByCheckInDateDesc(userId: Long): AttendanceLog?

    fun findByUserIdAndCheckInDateBetweenOrderByCheckInDateAsc(
        userId: Long,
        start: LocalDate,
        end: LocalDate,
    ): List<AttendanceLog>
}
