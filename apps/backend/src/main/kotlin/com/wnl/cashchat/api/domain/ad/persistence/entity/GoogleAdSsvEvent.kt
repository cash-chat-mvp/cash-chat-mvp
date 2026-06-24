package com.wnl.cashchat.api.domain.ad.persistence.entity

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

@Entity
@Table(
    name = "google_ad_ssv_events",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_google_ad_ssv_events_transaction_id",
            columnNames = ["transaction_id"]
        )
    ]
)
class GoogleAdSsvEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "transaction_id", nullable = false)
    val transactionId: String,

    @Column(name = "user_id", nullable = false)
    val userId: String,

    @Column(name = "reward_amount", nullable = false)
    val rewardAmount: Int,

    @Column(name = "reward_item", nullable = false)
    val rewardItem: String,

    @Column(name = "ad_unit", nullable = false)
    val adUnit: String,

    @Column(name = "key_id", nullable = false)
    val keyId: Long,

    @Column(name = "raw_query_string", nullable = false, columnDefinition = "TEXT")
    val rawQueryString: String,

    @Column(name = "custom_data", nullable = true, length = 1024)
    val customData: String? = null,
) : BaseEntity() {
    @Enumerated(EnumType.STRING)
    @Column(name = "reward_status", nullable = false)
    var rewardStatus: RewardStatus = RewardStatus.VERIFIED
        private set

    fun markGranted() {
        rewardStatus = RewardStatus.GRANTED
    }

    fun markRejected(reason: RewardStatus) {
        require(reason == RewardStatus.REJECTED_INVALID_NONCE || reason == RewardStatus.REJECTED_OVER_QUOTA) {
            "reason must be a REJECTED_* status"
        }
        rewardStatus = reason
    }

    init {
        require(transactionId.isNotBlank()) { "Transaction id must not be blank" }
        require(userId.isNotBlank()) { "User id must not be blank" }
        require(rewardAmount > 0) { "Reward amount must be positive" }
        require(rewardItem.isNotBlank()) { "Reward item must not be blank" }
        require(adUnit.isNotBlank()) { "Ad unit must not be blank" }
        require(keyId >= 0) { "Key id must be non-negative" }
        require(rawQueryString.isNotBlank()) { "Raw query string must not be blank" }
    }
}

enum class RewardStatus {
    VERIFIED,
    GRANTED,
    REJECTED_INVALID_NONCE,
    REJECTED_OVER_QUOTA,
}
