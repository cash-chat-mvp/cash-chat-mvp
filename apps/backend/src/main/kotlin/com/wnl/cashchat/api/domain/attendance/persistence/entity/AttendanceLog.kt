package com.wnl.cashchat.api.domain.attendance.persistence.entity

import com.wnl.cashchat.api.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate

/**
 * 사용자의 일자별 출석 도장 1건. (user_id, check_in_date) 유니크로 동일 일자 중복을 차단한다.
 * streakDayCount = 해당 출석 시점의 연속 출석 일차.
 */
@Entity
@Table(
    name = "attendance_log",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_attendance_log_user_date", columnNames = ["user_id", "check_in_date"])
    ]
)
class AttendanceLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "check_in_date", nullable = false)
    val checkInDate: LocalDate,

    @Column(name = "streak_day_count", nullable = false)
    val streakDayCount: Int,
) : BaseEntity()
