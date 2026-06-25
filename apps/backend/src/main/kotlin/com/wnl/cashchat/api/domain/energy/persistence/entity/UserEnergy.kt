package com.wnl.cashchat.api.domain.energy.persistence.entity

import com.wnl.cashchat.api.common.entity.BaseEntity
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * 유저별 밥(채팅 연료) 지갑. 돈(cashablePt)과 별개이며, 채팅은 이 지갑만 소모한다.
 * 상한(maxEnergy)·보정 floor 는 정책값이라 호출자가 주입한다(엔티티가 config 를 모름).
 */
@Entity
@Table(
    name = "user_energy",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id"])]
)
class UserEnergy(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    val user: User,

    energy: Int = 0,
) : BaseEntity() {
    @Column(nullable = false)
    var energy: Int = energy
        private set

    /** 채팅 스트림 진행 중 예약된 밥. 정상 완료 시 소진(settle), 실패/취소 시 환불(refund). */
    @Column(name = "reserved_energy", nullable = false)
    var reservedEnergy: Int = 0
        private set

    init {
        require(energy >= 0) { "Energy must be non-negative" }
    }

    fun charge(amount: Int, maxEnergy: Int) {
        require(amount >= 0) { "Energy amount must be non-negative" }
        energy = minOf(maxEnergy, energy + amount)
    }

    fun consume() {
        check(energy >= 1) { "Not enough energy" }
        energy -= 1
    }

    /** 채팅 진입 시 밥 1개를 available → reserved 로 옮긴다(게이트). */
    fun reserve() {
        check(energy >= 1) { "Not enough energy" }
        energy -= 1
        reservedEnergy += 1
    }

    /** 채팅 정상 완료 시 예약분 1개를 최종 소진한다. */
    fun settleReserved() {
        check(reservedEnergy >= 1) { "No reserved energy to settle" }
        reservedEnergy -= 1
    }

    /** 채팅 실패/취소 시 예약분 1개를 available 로 되돌린다. */
    fun refundReserved() {
        check(reservedEnergy >= 1) { "No reserved energy to refund" }
        reservedEnergy -= 1
        energy += 1
    }

    /** 가입/진화 직후 1회 보정: 현재 밥이 floor 미만이면 floor 까지 올리고, 이미 많으면 그대로 둔다. */
    fun boostTo(floor: Int) {
        if (energy < floor) energy = floor
    }
}
