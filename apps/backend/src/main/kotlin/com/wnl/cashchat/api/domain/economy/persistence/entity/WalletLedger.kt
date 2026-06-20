package com.wnl.cashchat.api.domain.economy.persistence.entity

import com.wnl.cashchat.api.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "wallet_ledger",
    uniqueConstraints = [UniqueConstraint(name = "uq_wallet_ledger_idempotency_key", columnNames = ["idempotency_key"])],
    indexes = [Index(name = "idx_wallet_ledger_user", columnList = "user_id")]
)
class WalletLedger(
    @Column(name = "user_id", nullable = false) val userId: Long,
    @Enumerated(EnumType.STRING) @Column(name = "tx_type", nullable = false, length = 40) val type: WalletTxType,
    @Column(name = "delta", nullable = false) val delta: Long,
    @Column(name = "balance_after", nullable = false) val balanceAfter: Long,
    @Column(name = "reference_id", length = 255) val referenceId: String?,
    @Column(name = "idempotency_key", nullable = false, length = 255) val idempotencyKey: String,
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
) : BaseEntity()
