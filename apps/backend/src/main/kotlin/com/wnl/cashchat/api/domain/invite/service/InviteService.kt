package com.wnl.cashchat.api.domain.invite.service

import com.wnl.cashchat.api.domain.invite.persistence.repository.InviteCodeRepository
import com.wnl.cashchat.api.domain.invite.persistence.repository.InviteRedemptionRepository
import com.wnl.cashchat.api.domain.invite.properties.InviteProperties
import com.wnl.cashchat.api.domain.user.persistence.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

/**
 * 친구 초대 — 추천 코드 발급 및 redeem(Task 4에서 추가).
 * 코드 발급은 insertIfAbsent + findForUpdate get-or-create(offerwall 토큰과 동일 패턴)이며,
 * code UNIQUE 충돌 시 새 코드로 재시도한다.
 */
@Service
class InviteService(
    private val inviteCodeRepository: InviteCodeRepository,
    private val inviteRedemptionRepository: InviteRedemptionRepository,
    private val inviteCodeGenerator: InviteCodeGenerator,
    private val userRepository: UserRepository,
    private val properties: InviteProperties,
) {
    @Transactional
    fun getMyInvite(userId: Long, now: Instant): MyInviteView =
        MyInviteView(
            myCode = getOrCreateCode(userId),
            invitedCount = inviteRedemptionRepository.countByInviterUserId(userId),
            redeemAvailable = isRedeemEligible(userId, now),
            rewardCoin = properties.inviterRewardCoin,
            rewardEnergy = properties.inviteeRewardEnergy,
        )

    private fun getOrCreateCode(userId: Long): String {
        inviteCodeRepository.findByUserId(userId)?.let { return it.code }
        repeat(MAX_CODE_ATTEMPTS) {
            val code = inviteCodeGenerator.generate(properties.codeLength)
            inviteCodeRepository.insertIfAbsent(userId, code)
            // null = code UNIQUE 가 다른 사용자 행과 충돌해 우리 행이 안 들어감 → 새 코드로 재시도.
            inviteCodeRepository.findForUpdate(userId)?.let { return it.code }
        }
        throw IllegalStateException("Failed to allocate invite code for userId=$userId")
    }

    private fun isRedeemEligible(userId: Long, now: Instant): Boolean =
        !inviteRedemptionRepository.existsByInviteeUserId(userId) && isWithinWindow(userId, now)

    private fun isWithinWindow(userId: Long, now: Instant): Boolean {
        val user = userRepository.findById(userId).orElse(null) ?: return false
        return now.isBefore(user.createdAt.plus(Duration.ofDays(properties.redeemWindowDays.toLong())))
    }

    private companion object {
        private const val MAX_CODE_ATTEMPTS = 10
    }
}
