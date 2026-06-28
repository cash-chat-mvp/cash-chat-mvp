package com.wnl.cashchat.api.domain.invite.persistence.entity

import com.wnl.cashchat.api.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 추천 코드 입력(redeem) 원장. 사용자당 1회만 가능 — invitee_user_id UNIQUE 가
 * "1인 1회 + (멱등성 없는) 에너지 중복 지급"의 1차 방어선이다.
 */
@Entity
@Table(name = "invite_redemptions")
class InviteRedemption(
    @Column(name = "invitee_user_id", nullable = false)
    val inviteeUserId: Long,

    @Column(name = "inviter_user_id", nullable = false)
    val inviterUserId: Long,

    @Column(name = "code", nullable = false, length = 16)
    val code: String,

    @Column(name = "awarded_energy", nullable = false)
    val awardedEnergy: Int,

    @Column(name = "awarded_coin", nullable = false)
    val awardedCoin: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    val status: InviteRedemptionStatus,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
) : BaseEntity()
