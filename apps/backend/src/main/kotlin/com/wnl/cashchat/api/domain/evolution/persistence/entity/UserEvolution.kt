package com.wnl.cashchat.api.domain.evolution.persistence.entity

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
 * 유저별 진화(육성) 레벨 상태. UserPoint 처럼 user 당 1행(OneToOne).
 * 레벨 상한은 MAX_LEVEL 상수로 방어하고, 실제 진화 가능 여부(전이 규칙)는
 * EvolutionProperties.ruleFor(level) 로 EvolutionService 가 판단한다.
 */
@Entity
@Table(
    name = "user_evolution",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id"])]
)
class UserEvolution(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    val user: User,

    level: Int = 1,
) : BaseEntity() {
    @Column(nullable = false)
    var level: Int = level
        private set

    init {
        require(level >= 1) { "Evolution level must be >= 1" }
    }

    fun isMaxLevel(): Boolean = level >= MAX_LEVEL

    fun levelUp() {
        check(level < MAX_LEVEL) { "Already at max evolution level $MAX_LEVEL" }
        level += 1
    }

    companion object {
        const val MAX_LEVEL = 5
    }
}