package com.wnl.cashchat.api.domain.invite.service

import com.wnl.cashchat.api.domain.energy.service.EnergyService
import com.wnl.cashchat.api.domain.invite.exception.AlreadyRedeemedException
import com.wnl.cashchat.api.domain.invite.exception.InvalidCodeException
import com.wnl.cashchat.api.domain.invite.exception.NotEligibleException
import com.wnl.cashchat.api.domain.invite.exception.SelfReferralException
import com.wnl.cashchat.api.domain.invite.persistence.entity.InviteRedemption
import com.wnl.cashchat.api.domain.invite.persistence.entity.InviteRedemptionStatus
import com.wnl.cashchat.api.domain.invite.persistence.repository.InviteCodeRepository
import com.wnl.cashchat.api.domain.invite.persistence.repository.InviteRedemptionRepository
import com.wnl.cashchat.api.domain.invite.properties.InviteProperties
import com.wnl.cashchat.api.domain.point.persistence.entity.PointTransactionReason
import com.wnl.cashchat.api.domain.point.service.UserPointService
import com.wnl.cashchat.api.domain.user.persistence.repository.UserRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
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
    private val energyService: EnergyService,
    private val userPointService: UserPointService,
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

    // READ_COMMITTED: REPEATABLE READ 스냅샷이 FOR UPDATE 락 취득 전에 찍히면
    // 그 이후에 실행되는 일반 SELECT(countByInviterUserIdAndStatus)가 이전 스냅샷을 바라봐
    // 다른 스레드가 이미 커밋한 GRANTED 행을 보지 못해 cap을 초과할 수 있다.
    // READ_COMMITTED는 매 SELECT마다 현재 커밋된 데이터를 읽으므로 이 문제가 없다.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun redeem(inviteeUserId: Long, rawCode: String, now: Instant): RedeemResult {
        val code = rawCode.trim().uppercase()
        val inviteCode = inviteCodeRepository.findByCode(code) ?: throw InvalidCodeException()
        val inviterUserId = inviteCode.userId
        if (inviterUserId == inviteeUserId) throw SelfReferralException()
        if (inviteRedemptionRepository.existsByInviteeUserId(inviteeUserId)) throw AlreadyRedeemedException()
        if (!isWithinWindow(inviteeUserId, now)) throw NotEligibleException()

        // 초대자(invite_codes) 행을 비관락으로 잡아 같은 코드의 동시 redeem 을 초대자별로 직렬화한다.
        // 이 락이 없으면 서로 다른 가입자가 동시에 cap 검사를 통과해 상한을 초과 적립할 수 있다
        // (invitee_user_id UNIQUE 는 초대자 상한 경합을 막지 못함).
        inviteCodeRepository.findForUpdate(inviterUserId)
            ?: throw IllegalStateException("invite_codes row missing for inviterUserId=$inviterUserId")

        val grantsCoin = inviteRedemptionRepository
            .countByInviterUserIdAndStatus(inviterUserId, InviteRedemptionStatus.GRANTED) < properties.inviterCap
        val status = if (grantsCoin) InviteRedemptionStatus.GRANTED else InviteRedemptionStatus.GRANTED_INVITER_CAPPED
        val awardedCoin = if (grantsCoin) properties.inviterRewardCoin else 0L

        // invitee_user_id UNIQUE 가 1인1회 + 에너지 중복지급의 최종 방어선.
        // 동시 도착한 두 번째 redeem 은 여기서 제약 위반 → 트랜잭션 전체 롤백 → 409(ALREADY_REDEEMED).
        try {
            inviteRedemptionRepository.saveAndFlush(
                InviteRedemption(
                    inviteeUserId = inviteeUserId,
                    inviterUserId = inviterUserId,
                    code = code,
                    awardedEnergy = properties.inviteeRewardEnergy,
                    awardedCoin = awardedCoin,
                    status = status,
                )
            )
        } catch (e: DataIntegrityViolationException) {
            throw AlreadyRedeemedException()
        }

        energyService.charge(inviteeUserId, properties.inviteeRewardEnergy)
        if (grantsCoin) {
            userPointService.recordTransaction(
                userId = inviterUserId,
                delta = properties.inviterRewardCoin,
                reason = PointTransactionReason.REFERRAL,
                idempotencyKey = "referral:$inviteeUserId",
            )
        }
        return RedeemResult(awardedEnergy = properties.inviteeRewardEnergy, status = status)
    }

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
