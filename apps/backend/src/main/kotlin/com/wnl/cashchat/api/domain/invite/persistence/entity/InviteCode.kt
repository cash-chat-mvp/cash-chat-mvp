package com.wnl.cashchat.api.domain.invite.persistence.entity

import com.wnl.cashchat.api.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/** 사용자당 고유 추천 코드(공유용). user_id 가 PK 이므로 사용자당 1행. */
@Entity
@Table(name = "invite_codes")
class InviteCode(
    @Id
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "code", nullable = false, length = 16)
    val code: String,
) : BaseEntity()
