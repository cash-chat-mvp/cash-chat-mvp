package com.wnl.cashchat.api.domain.attendance.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * 마일스톤 일차의 부가 보상 아이템 정의. Phase 1에서는 정의·미리보기 용도이며 실제 인벤토리 지급은 하지 않는다.
 *
 * (day_count, item_code) 유니크 제약이 day_count 선두 인덱스를 제공하므로 별도 단독 인덱스는 두지 않는다.
 */
@Entity
@Table(
    name = "attendance_reward_bonus",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_attendance_reward_bonus_day_item", columnNames = ["day_count", "item_code"])
    ]
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
