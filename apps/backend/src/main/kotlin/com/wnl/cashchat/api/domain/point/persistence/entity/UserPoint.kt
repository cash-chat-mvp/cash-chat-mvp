package com.wnl.cashchat.api.domain.point.persistence.entity

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

@Entity
@Table(
    name = "user_points",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id"])]
)
class UserPoint(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    val user: User,

    balance: Long = 0,
) : BaseEntity() {
    @Column(nullable = false)
    var balance: Long = balance
        private set

    init {
        require(balance >= 0) { "Point balance must be non-negative" }
    }

    fun charge(amount: Long) {
        require(amount >= 0) { "Point amount must be non-negative" }
        balance = Math.addExact(balance, amount)
    }

    fun deduct(cost: Long) {
        require(cost >= 0) { "Point cost must be non-negative" }
        require(balance >= cost) { "Point balance must be non-negative" }
        balance -= cost
    }
}
