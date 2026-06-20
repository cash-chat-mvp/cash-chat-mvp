package com.wnl.cashchat.api.domain.offerwall.persistence.entity

import com.wnl.cashchat.api.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * TNK 오퍼월 사용자 식별용 불투명 토큰. 사용자당 1행(안정적·재사용).
 * 프론트가 TNK SDK setUserName 에 이 token 을 넣고, 콜백의 md_user_nm 으로 되돌아온다.
 */
@Entity
@Table(name = "offerwall_user_tokens")
class OfferwallUserToken(
    @Id
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "token", nullable = false, length = 64)
    val token: String,
) : BaseEntity()
