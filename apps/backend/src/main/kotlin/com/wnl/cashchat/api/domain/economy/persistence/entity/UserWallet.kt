package com.wnl.cashchat.api.domain.economy.persistence.entity

import com.wnl.cashchat.api.common.entity.BaseEntity
import com.wnl.cashchat.api.domain.economy.exception.EnergyCapExceededException
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

@Entity
@Table(name = "user_wallet", uniqueConstraints = [UniqueConstraint(columnNames = ["user_id"])])
class UserWallet(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    val user: User,
) : BaseEntity() {
    @Column(name = "energy_available", nullable = false)
    var energyAvailable: Long = 0
        private set

    @Column(name = "energy_reserved", nullable = false)
    var energyReserved: Long = 0
        private set

    @Column(name = "pending_cashable_pt", nullable = false)
    var pendingCashablePt: Long = 0
        private set

    @Column(name = "confirmed_cashable_pt", nullable = false)
    var confirmedCashablePt: Long = 0
        private set

    @Column(name = "evolution_level", nullable = false)
    var evolutionLevel: Int = 1
        private set

    @Column(name = "evolution_exp", nullable = false)
    var evolutionExp: Long = 0
        private set

    @Column(name = "evolution_fail_stack", nullable = false)
    var evolutionFailStack: Int = 0
        private set

    fun grantEnergy(amount: Long, maxEnergy: Long) {
        require(amount >= 0) { "Energy amount must be non-negative" }
        if (Math.addExact(energyAvailable, amount) > maxEnergy) throw EnergyCapExceededException()
        energyAvailable += amount
    }
    fun reserveEnergy(amount: Long = 1) {
        require(amount >= 0) { "Reserve amount must be non-negative" }
        require(energyAvailable >= amount) { "Insufficient available energy" }
        energyAvailable -= amount; energyReserved += amount
    }
    fun consumeReserved(amount: Long = 1) {
        require(amount >= 0) { "Consume amount must be non-negative" }
        require(energyReserved >= amount) { "Insufficient reserved energy" }
        energyReserved -= amount
    }
    fun refundReserved(amount: Long = 1) {
        require(amount >= 0) { "Refund amount must be non-negative" }
        require(energyReserved >= amount) { "Insufficient reserved energy" }
        energyReserved -= amount; energyAvailable += amount
    }
    fun addPendingPt(amount: Long) {
        require(amount >= 0) { "Pending point amount must be non-negative" }
        pendingCashablePt = Math.addExact(pendingCashablePt, amount)
    }
    fun confirmPending(amount: Long) {
        require(amount >= 0) { "Confirm amount must be non-negative" }
        require(pendingCashablePt >= amount) { "Insufficient pending points" }
        pendingCashablePt -= amount; confirmedCashablePt = Math.addExact(confirmedCashablePt, amount)
    }
    fun addExp(amount: Long) {
        require(amount >= 0) { "Exp amount must be non-negative" }
        evolutionExp = Math.addExact(evolutionExp, amount)
    }
}
