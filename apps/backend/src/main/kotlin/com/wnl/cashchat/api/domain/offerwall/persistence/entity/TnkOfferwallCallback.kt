package com.wnl.cashchat.api.domain.offerwall.persistence.entity

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

/**
 * TNK 서버 포스트백 원장. seq_id 당 1행(UNIQUE). 멱등 INSERT 로 PENDING 상태로 먼저 생성한 뒤
 * 행 락(SELECT ... FOR UPDATE)을 잡고 검증·적립을 진행해 동일 seq_id 동시/중복 콜백을 직렬화한다.
 * status 는 향후 CANCELED 등 환수 상태로 확장 가능(현재 자동 환수는 범위 외).
 */
@Entity
@Table(
    name = "tnk_offerwall_callbacks",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_tnk_offerwall_callbacks_seq_id", columnNames = ["seq_id"])
    ]
)
class TnkOfferwallCallback(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "seq_id", nullable = false, length = 128)
    val seqId: String,

    @Column(name = "md_user_nm", nullable = false, length = 64)
    val mdUserNm: String,

    @Column(name = "pay_pnt", nullable = false)
    val payPnt: Long,

    @Column(name = "raw_query", nullable = false, columnDefinition = "TEXT")
    val rawQuery: String,
) : BaseEntity() {
    @Column(name = "coin_amount", nullable = false)
    var coinAmount: Long = 0
        private set

    @Column(name = "user_id")
    var userId: Long? = null
        private set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: TnkOfferwallStatus = TnkOfferwallStatus.PENDING
        private set

    fun markGranted(userId: Long, coinAmount: Long) {
        this.userId = userId
        this.coinAmount = coinAmount
        this.status = TnkOfferwallStatus.GRANTED
    }

    fun markRejected(status: TnkOfferwallStatus) {
        require(status == TnkOfferwallStatus.REJECTED_BAD_SIGNATURE || status == TnkOfferwallStatus.REJECTED_UNKNOWN_USER) {
            "status must be a REJECTED_* value"
        }
        this.status = status
    }
}

enum class TnkOfferwallStatus {
    PENDING,
    GRANTED,
    REJECTED_BAD_SIGNATURE,
    REJECTED_UNKNOWN_USER,
}
