package com.wnl.cashchat.api.domain.chat.persistence.entity

import com.wnl.cashchat.api.common.entity.BaseEntity
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(
    name = "chat_reward_settlement",
    uniqueConstraints = [UniqueConstraint(
        name = "uq_chat_reward_settlement_user_msg_type",
        columnNames = ["user_id", "message_id", "reward_type"],
    )],
    indexes = [Index(name = "idx_chat_reward_settlement_message", columnList = "message_id")],
)
class ChatRewardSettlement(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
    @Column(name = "user_id", nullable = false) val userId: Long,
    @Column(name = "message_id", nullable = false) val messageId: String,
    @Enumerated(EnumType.STRING) @Column(name = "reward_type", nullable = false, length = 30)
    val rewardType: ChatRewardType = ChatRewardType.CHAT_REWARD,
    @Column(name = "conversation_id", nullable = false) val conversationId: Long,
) : BaseEntity() {
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 30)
    var status: SettlementStatus = SettlementStatus.ENERGY_RESERVED
        private set
    @Column(name = "assistant_message_id") var assistantMessageId: Long? = null
        private set
    @Column(name = "energy_delta", nullable = false) var energyDelta: Long = 0
        private set
    @Column(name = "pending_pt_delta", nullable = false) var pendingPtDelta: Long = 0
        private set
    @Column(name = "evolution_exp_delta", nullable = false) var evolutionExpDelta: Long = 0
        private set
    @Column(name = "settled_at") var settledAt: Instant? = null
        private set

    fun markGenerating() { status = SettlementStatus.GENERATING }
    fun markSettled(assistantMessageId: Long, energyDelta: Long, pendingPtDelta: Long, evolutionExpDelta: Long, settledAt: Instant) {
        this.assistantMessageId = assistantMessageId
        this.energyDelta = energyDelta; this.pendingPtDelta = pendingPtDelta; this.evolutionExpDelta = evolutionExpDelta
        this.settledAt = settledAt; status = SettlementStatus.SETTLED
    }
    fun markRefunded(assistantMessageId: Long?) {
        this.assistantMessageId = assistantMessageId; status = SettlementStatus.REFUNDED
    }
    fun markFailed(assistantMessageId: Long?) {
        this.assistantMessageId = assistantMessageId; status = SettlementStatus.FAILED
    }
}
