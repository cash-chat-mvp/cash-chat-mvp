package com.wnl.cashchat.api.domain.attendance.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table

/**
 * 마일스톤 일차의 부가 보상 아이템 정의. Phase 1에서는 정의·미리보기 용도이며 실제 인벤토리 지급은 하지 않는다.
 */
@Entity
@Table(
    name = "attendance_reward_bonus",
    indexes = [Index(name = "idx_attendance_reward_bonus_day", columnList = "day_count")]
)
class AttendanceRewardBonus(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "day_count", nullable = false)
    val dayCount: Int,

    @Column(name = "item_code", nullable = false, length = 50)
    val itemCode: String,

    @Column(nullable = false)
    val quantity: Int,
)
