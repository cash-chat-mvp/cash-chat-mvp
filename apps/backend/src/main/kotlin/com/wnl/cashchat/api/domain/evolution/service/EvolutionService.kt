package com.wnl.cashchat.api.domain.evolution.service

import com.wnl.cashchat.api.domain.economy.exception.FeatureDisabledException
import com.wnl.cashchat.api.domain.economy.persistence.entity.WalletLedger
import com.wnl.cashchat.api.domain.economy.persistence.entity.WalletTxType
import com.wnl.cashchat.api.domain.economy.persistence.repository.WalletLedgerRepository
import com.wnl.cashchat.api.domain.economy.properties.EconomyProperties
import com.wnl.cashchat.api.domain.economy.properties.EvolutionProperties
import com.wnl.cashchat.api.domain.economy.service.WalletService
import com.wnl.cashchat.api.domain.evolution.exception.EvolutionAttemptNotFoundException
import com.wnl.cashchat.api.domain.evolution.exception.EvolutionInsufficientExpException
import com.wnl.cashchat.api.domain.evolution.exception.EvolutionLevelMismatchException
import com.wnl.cashchat.api.domain.evolution.exception.EvolutionMaxLevelException
import com.wnl.cashchat.api.domain.evolution.persistence.entity.EvolutionAttempt
import com.wnl.cashchat.api.domain.evolution.persistence.entity.EvolutionResult
import com.wnl.cashchat.api.domain.evolution.persistence.repository.EvolutionAttemptRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.math.min

data class EvolutionMe(
    val level: Int, val exp: Long, val failStack: Int, val maxLevel: Int,
    val requiredExp: Long?, val baseSuccessRate: Double?, val finalSuccessRate: Double?,
    val canAttempt: Boolean,
)

@Service
class EvolutionService(
    private val walletService: WalletService,
    private val evolutionAttemptRepository: EvolutionAttemptRepository,
    private val walletLedgerRepository: WalletLedgerRepository,
    private val evolutionProperties: EvolutionProperties,
    private val economyProperties: EconomyProperties,
    private val evolutionRandom: EvolutionRandom,
) {
    @Transactional
    fun attempt(userId: Long, attemptKey: String, expectedLevel: Int): EvolutionAttempt {
        if (!economyProperties.evolutionEnabled) throw FeatureDisabledException("진화 기능이 일시 중지되었습니다.")
        val wallet = walletService.ensureForUpdate(userId)               // 행 락 먼저
        evolutionAttemptRepository.findByUserIdAndAttemptKey(userId, attemptKey)?.let { return it } // I14 멱등

        if (expectedLevel != wallet.evolutionLevel)
            throw EvolutionLevelMismatchException(expectedLevel, wallet.evolutionLevel)
        val policy = evolutionProperties.policyFor(wallet.evolutionLevel)
            ?: throw EvolutionMaxLevelException(wallet.evolutionLevel)
        if (wallet.evolutionExp < policy.requiredExp)
            throw EvolutionInsufficientExpException(policy.requiredExp, wallet.evolutionExp)

        val levelBefore = wallet.evolutionLevel
        val failStackBefore = wallet.evolutionFailStack
        val expBefore = wallet.evolutionExp
        val finalRate = min(1.0, policy.baseSuccessRate + failStackBefore * evolutionProperties.failStackBonus)
        val roll = evolutionRandom.roll()
        val success = roll < finalRate

        if (success) wallet.applyEvolutionSuccess() else wallet.applyEvolutionFailure(policy.failKeepRatio)

        // EXP 변동 원장(멱등 키 = evolution:$attemptKey). 성공/실패 모두 exp 감소.
        val expDelta = wallet.evolutionExp - expBefore
        walletLedgerRepository.findByIdempotencyKey("evolution:$attemptKey") ?: walletLedgerRepository.save(
            WalletLedger(
                userId = userId, type = WalletTxType.EXP_CONSUMED, delta = expDelta,
                balanceAfter = wallet.evolutionExp, referenceId = attemptKey,
                idempotencyKey = "evolution:$attemptKey",
            ),
        )

        val attempt = EvolutionAttempt(
            userId = userId, attemptKey = attemptKey,
            levelBefore = levelBefore, levelAfter = wallet.evolutionLevel,
            requiredExp = policy.requiredExp, baseSuccessRate = policy.baseSuccessRate,
            failStackBefore = failStackBefore, finalSuccessRate = finalRate, rollValue = roll,
            result = if (success) EvolutionResult.SUCCESS else EvolutionResult.FAIL,
            expAfter = wallet.evolutionExp, failStackAfter = wallet.evolutionFailStack,
            policyVersion = evolutionProperties.policyVersion,
        )
        return try {
            evolutionAttemptRepository.saveAndFlush(attempt)
        } catch (e: DataIntegrityViolationException) { // 동시 동일 key 최종 방어
            evolutionAttemptRepository.findByUserIdAndAttemptKey(userId, attemptKey) ?: throw e
        }
    }

    @Transactional(readOnly = true)
    fun me(userId: Long): EvolutionMe {
        val w = walletService.snapshot(userId)
        val policy = evolutionProperties.policyFor(w.evolutionLevel)
        val finalRate = policy?.let { min(1.0, it.baseSuccessRate + w.evolutionFailStack * evolutionProperties.failStackBonus) }
        val canAttempt = policy != null && w.evolutionExp >= policy.requiredExp
        return EvolutionMe(
            level = w.evolutionLevel, exp = w.evolutionExp, failStack = w.evolutionFailStack,
            maxLevel = evolutionProperties.maxLevel, requiredExp = policy?.requiredExp,
            baseSuccessRate = policy?.baseSuccessRate, finalSuccessRate = finalRate, canAttempt = canAttempt,
        )
    }

    @Transactional(readOnly = true)
    fun findAttempt(userId: Long, attemptId: Long): EvolutionAttempt =
        evolutionAttemptRepository.findByIdAndUserId(attemptId, userId)
            ?: throw EvolutionAttemptNotFoundException(attemptId)
}
