package com.wnl.cashchat.api.domain.attendance.persistence.repository

import com.wnl.cashchat.api.domain.attendance.persistence.entity.AttendanceReward
import org.springframework.data.jpa.repository.JpaRepository

interface AttendanceRewardRepository : JpaRepository<AttendanceReward, Int> {
    fun findByDayCount(dayCount: Int): AttendanceReward?
}
