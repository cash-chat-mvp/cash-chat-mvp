package com.wnl.cashchat.api.domain.invite.persistence.repository

import com.wnl.cashchat.api.domain.invite.persistence.entity.InviteRedemption
import com.wnl.cashchat.api.domain.invite.persistence.entity.InviteRedemptionStatus
import org.springframework.data.jpa.repository.JpaRepository

interface InviteRedemptionRepository : JpaRepository<InviteRedemption, Long> {
    fun existsByInviteeUserId(inviteeUserId: Long): Boolean
    fun countByInviterUserId(inviterUserId: Long): Long
    fun countByInviterUserIdAndStatus(inviterUserId: Long, status: InviteRedemptionStatus): Long
}
