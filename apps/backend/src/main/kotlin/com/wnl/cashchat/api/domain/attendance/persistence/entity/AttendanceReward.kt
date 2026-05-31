package com.wnl.cashchat.api.domain.attendance.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 누적 일차별 코인 보상 시드. day_count=0 은 "기본 일일 보상"(비마일스톤·31일+ 폴백).
 */
@Entity
@Table(name = "attendance_reward")
class AttendanceReward(
    @Id
    @Column(name = "day_count", nullable = false)
    val dayCount: Int,

    @Column(nullable = false)
    val coin: Long,
)
