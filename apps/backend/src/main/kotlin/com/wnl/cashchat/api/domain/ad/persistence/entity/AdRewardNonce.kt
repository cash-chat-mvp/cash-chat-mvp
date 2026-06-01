package com.wnl.cashchat.api.domain.ad.persistence.entity

import com.wnl.cashchat.api.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 광고 시청 직전 서버가 발급하는 단일 사용·단기 nonce. nonce → 내부 userId 매핑.
 * 클라이언트가 SSV user_id 필드에 이 nonce 를 실어 보낸다.
 */
@Entity
@Table(name = "ad_reward_nonce")
class AdRewardNonce(
    @Id
    @Column(name = "nonce", nullable = false, length = 64)
    val nonce: String,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    used: Boolean = false,
) : BaseEntity() {
    @Column(name = "used", nullable = false)
    var used: Boolean = used
        private set

    fun markUsed() {
        used = true
    }

    fun isUsable(now: Instant): Boolean = !used && expiresAt.isAfter(now)
}
