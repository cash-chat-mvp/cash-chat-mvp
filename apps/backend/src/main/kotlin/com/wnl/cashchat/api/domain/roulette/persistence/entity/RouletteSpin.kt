package com.wnl.cashchat.api.domain.roulette.persistence.entity

import com.wnl.cashchat.api.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate

@Entity
@Table(
    name = "roulette_spin",
    uniqueConstraints = [UniqueConstraint(name = "uq_roulette_spin_nonce", columnNames = ["nonce"])]
)
class RouletteSpin(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "kst_date", nullable = false)
    val kstDate: LocalDate,

    @Enumerated(EnumType.STRING)
    @Column(name = "spin_type", nullable = false, length = 16)
    val spinType: RouletteSpinType,

    @Enumerated(EnumType.STRING)
    @Column(name = "prize", nullable = false, length = 32)
    val prize: RoulettePrize,

    @Column(name = "prize_energy", nullable = false)
    val prizeEnergy: Int,

    @Column(name = "awarded_energy", nullable = false)
    val awardedEnergy: Int,

    @Column(name = "energy_after", nullable = false)
    val energyAfter: Int,

    @Column(name = "segment_index", nullable = false)
    val segmentIndex: Int,

    @Column(name = "nonce", nullable = true, length = 64)
    val nonce: String? = null,
) : BaseEntity()
