package com.wnl.cashchat.api.domain.quality.persistence.entity

import com.wnl.cashchat.api.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 전체 유저 공용 프리미엄 재원 풀 (singleton row, id=1).
 * 잔액은 centi-pt (pt×100) 정수로 저장해 부동소수 누적 오차를 방지한다.
 * I6 불변식: balanceCentiPt ≥ 0 항상 유지 (tryConsume 이 음수 진입 차단).
 */
@Entity
@Table(name = "shared_quality_pool")
class SharedQualityPool(
    @Id
    val id: Long = 1L,

    balanceCentiPt: Long = 0L,
) : BaseEntity() {

    @Column(nullable = false)
    var balanceCentiPt: Long = balanceCentiPt
        private set

    init {
        require(balanceCentiPt >= 0) { "Initial balanceCentiPt must be >= 0" }
    }

    /**
     * 풀에 [amount] centi-pt 를 적립한다.
     * @throws IllegalArgumentException amount < 0
     */
    fun accrue(amount: Long) {
        require(amount >= 0) { "Accrue amount must be >= 0, was $amount" }
        balanceCentiPt = Math.addExact(balanceCentiPt, amount)
    }

    /**
     * 풀에서 [delta] centi-pt 를 인출 시도한다.
     * 잔액 ≥ delta 이면 차감 후 true, 그렇지 않으면 불변 후 false (I6).
     * @throws IllegalArgumentException delta < 0
     */
    fun tryConsume(delta: Long): Boolean {
        require(delta >= 0) { "Consume delta must be >= 0, was $delta" }
        if (balanceCentiPt < delta) return false
        balanceCentiPt -= delta
        return true
    }
}
