package com.wnl.cashchat.api.domain.attendance.persistence.repository

import com.wnl.cashchat.api.domain.attendance.persistence.entity.AttendanceRewardBonus
import org.springframework.data.jpa.repository.JpaRepository

interface AttendanceRewardBonusRepository : JpaRepository<AttendanceRewardBonus, Long> {
    fun findByDayCount(dayCount: Int): List<AttendanceRewardBonus>
}
