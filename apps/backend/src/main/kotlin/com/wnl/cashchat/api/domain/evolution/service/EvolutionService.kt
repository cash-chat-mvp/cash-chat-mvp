package com.wnl.cashchat.api.domain.evolution.service

import com.wnl.cashchat.api.domain.energy.service.EnergyService
import com.wnl.cashchat.api.domain.evolution.exception.AlreadyMaxLevelException
import com.wnl.cashchat.api.domain.evolution.persistence.entity.EvolutionAttempt
import com.wnl.cashchat.api.domain.evolution.persistence.entity.UserEvolution
import com.wnl.cashchat.api.domain.evolution.persistence.repository.EvolutionAttemptRepository
import com.wnl.cashchat.api.domain.evolution.persistence.repository.UserEvolutionRepository
import com.wnl.cashchat.api.domain.evolution.properties.EvolutionProperties
import com.wnl.cashchat.api.domain.point.persistence.entity.PointTransactionReason
import com.wnl.cashchat.api.domain.point.service.UserPointService
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 진화(육성) 레벨 조회와 진화 시도.
 *
 * attempt 는 단일 @Transactional 안에서 (1) user_evolution 행을 비관락으로 잡고 →
 * (2) 멱등성 키로 기존 시도를 조회해 있으면 그대로 반환(이중 차감 방지) →
 * (3) 전이 규칙이 없으면 최대 레벨(AlreadyMaxLevel) → (4) 포인트 차감(recordTransaction, 멱등) →
 * (5) 확률 판정 → 성공 시 레벨업 → (6) 시도 원장 INSERT 순으로 처리한다.
 * 포인트 차감이 실패(InsufficientPoints)하면 트랜잭션이 롤백되어 레벨업·원장 모두 기록되지 않는다.
 */
@Service
class EvolutionService(
    private val userEvolutionRepository: UserEvolutionRepository,
    private val evolutionAttemptRepository: EvolutionAttemptRepository,
    private val userPointService: UserPointService,
    private val probabilityRoller: ProbabilityRoller,
    private val evolutionProperties: EvolutionProperties,
    private val energyService: EnergyService,
) {
    fun ensureInitialized(user: User): UserEvolution =
        userEvolutionRepository.findByUserId(user.id) ?: createInitial(user)

    @Transactional(readOnly = true)
    fun getState(userId: Long): EvolutionStateResult {
        val evo = userEvolutionRepository.findByUserId(userId)
            ?: throw IllegalStateException("UserEvolution not initialized for userId=$userId")
        val rule = evolutionProperties.ruleFor(evo.level)
        return EvolutionStateResult(
            level = evo.level,
            isMaxLevel = rule == null,
            nextAttemptCost = rule?.attemptCost,
            nextSuccessRate = rule?.successRate,
        )
    }

    /** 채팅 완료 보상: 진화 경험치 적립(개정 모델 CC-283 R1). */
    @Transactional
    fun addExp(userId: Long, amount: Long) {
        val evo = userEvolutionRepository.findByUserIdForUpdate(userId)
            ?: throw IllegalStateException("UserEvolution not initialized for userId=$userId")
        evo.addExp(amount)
    }

    @Transactional
    fun attempt(userId: Long, idempotencyKey: String): EvolutionAttemptResult {
        val evo = userEvolutionRepository.findByUserIdForUpdate(userId)
            ?: throw IllegalStateException("UserEvolution not initialized for userId=$userId")

        evolutionAttemptRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)?.let { return it.toResult() }

        val rule = evolutionProperties.ruleFor(evo.level) ?: throw AlreadyMaxLevelException()
        val fromLevel = evo.level

        userPointService.recordTransaction(
            userId = userId,
            delta = -rule.attemptCost,
            reason = PointTransactionReason.EVOLUTION_ATTEMPT,
            idempotencyKey = "evolution:$userId:$idempotencyKey",
        )

        val success = probabilityRoller.succeeds(rule.successRate)
        if (success) {
            evo.levelUp()
            energyService.applyPostEvolutionBoost(userId)
        }

        evolutionAttemptRepository.save(
            EvolutionAttempt(
                userId = userId,
                fromLevel = fromLevel,
                cost = rule.attemptCost,
                success = success,
                resultLevel = evo.level,
                idempotencyKey = idempotencyKey,
            )
        )
        return EvolutionAttemptResult(success, fromLevel, evo.level, rule.attemptCost)
    }

    private fun createInitial(user: User): UserEvolution =
        try {
            userEvolutionRepository.saveAndFlush(UserEvolution(user = user, level = 1))
        } catch (e: DataIntegrityViolationException) {
            userEvolutionRepository.findByUserId(user.id) ?: throw e
        }

    private fun EvolutionAttempt.toResult() =
        EvolutionAttemptResult(success, fromLevel, resultLevel, cost)
}