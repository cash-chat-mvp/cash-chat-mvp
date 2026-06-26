package com.wnl.cashchat.api.domain.evolution.service

import com.wnl.cashchat.api.domain.energy.service.EnergyService
import com.wnl.cashchat.api.domain.evolution.exception.AlreadyMaxLevelException
import com.wnl.cashchat.api.domain.evolution.persistence.entity.EvolutionAttempt
import com.wnl.cashchat.api.domain.evolution.persistence.entity.UserEvolution
import com.wnl.cashchat.api.domain.evolution.persistence.repository.EvolutionAttemptRepository
import com.wnl.cashchat.api.domain.evolution.persistence.repository.UserEvolutionRepository
import com.wnl.cashchat.api.domain.evolution.properties.EvolutionProperties
import com.wnl.cashchat.api.domain.user.persistence.entity.User
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 진화(육성) 레벨 조회와 진화 시도.
 *
 * attempt 는 단일 @Transactional 안에서 (1) user_evolution 행을 비관락으로 잡고 →
 * (2) 멱등성 키로 기존 시도를 조회해 있으면 그대로 반환(이중 차감 방지) →
 * (3) 전이 규칙이 없으면 최대 레벨(AlreadyMaxLevel) → (4) 진화 경험치 차감(spendExp, 개정 모델 CC-283 R2) →
 * (5) 확률 판정 → 성공 시 레벨업 → (6) 시도 원장 INSERT 순으로 처리한다.
 * 경험치가 부족(InsufficientEvolutionExp)하면 트랜잭션이 롤백되어 레벨업·원장 모두 기록되지 않는다.
 * 비관락 + 멱등 키 조회가 이중 차감을 막으므로 차감 자체는 멱등 키가 필요 없다.
 */
@Service
class EvolutionService(
    private val userEvolutionRepository: UserEvolutionRepository,
    private val evolutionAttemptRepository: EvolutionAttemptRepository,
    private val evolutionStateInitializer: EvolutionStateInitializer,
    private val probabilityRoller: ProbabilityRoller,
    private val evolutionProperties: EvolutionProperties,
    private val energyService: EnergyService,
    private val timingSessionStore: TimingSessionStore,
    private val evolutionTimingJudge: EvolutionTimingJudge,
) {
    fun ensureInitialized(user: User): UserEvolution =
        evolutionStateInitializer.ensureInitialized(user)

    @Transactional(readOnly = true)
    fun getState(userId: Long): EvolutionStateResult {
        val evo = userEvolutionRepository.findByUserId(userId)
            ?: evolutionStateInitializer.initializeExistingUser(userId)
        val rule = evolutionProperties.ruleFor(evo.level)
        return EvolutionStateResult(
            level = evo.level,
            isMaxLevel = rule == null,
            nextAttemptCost = rule?.attemptCost,
            nextSuccessRate = rule?.successRate,
            currentExp = evo.exp,
        )
    }

    @Transactional(readOnly = true)
    fun getAttempts(userId: Long, limit: Int): List<EvolutionAttemptRecordResult> {
        require(limit in 1..100) { "limit must be in 1..100" }
        return evolutionAttemptRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit))
            .map {
                EvolutionAttemptRecordResult(
                    success = it.success,
                    fromLevel = it.fromLevel,
                    resultLevel = it.resultLevel,
                    cost = it.cost,
                    attemptedAt = it.createdAt,
                )
            }
    }

    /** 채팅 완료 보상: 진화 경험치 적립(개정 모델 CC-283 R1). */
    @Transactional
    fun addExp(userId: Long, amount: Long) {
        val evo = userEvolutionRepository.findByUserIdForUpdate(userId)
            ?: throw IllegalStateException("UserEvolution not initialized for userId=$userId")
        evo.addExp(amount)
    }

    @Transactional
    fun attempt(userId: Long, idempotencyKey: String, timing: TimingAttemptCommand? = null): EvolutionAttemptResult {
        val evo = userEvolutionRepository.findByUserIdForUpdate(userId)
            ?: throw IllegalStateException("UserEvolution not initialized for userId=$userId")

        evolutionAttemptRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)?.let { return it.toResult() }

        val rule = evolutionProperties.ruleFor(evo.level) ?: throw AlreadyMaxLevelException()
        val fromLevel = evo.level

        // 경험치 차감을 먼저 수행한다: 부족(InsufficientEvolutionExp)이면 여기서 롤백되어
        // 1회용 타이밍 세션(인메모리, 트랜잭션 비참여)이 소비되지 않는다.
        evo.spendExp(rule.attemptCost)

        // 타이밍 판정(있으면): 세션 소비(1회용) → 변조검증/등급 → 최종 확률.
        val judgement = timing?.let {
            val now = java.time.Instant.now()
            val session = timingSessionStore.consume(it.sessionId, userId, now)
            val elapsed = java.time.Duration.between(session.serverStartedAt, now).toMillis()
            evolutionTimingJudge.judge(it.releasedAtMs, elapsed, rule.successRate)
        }
        val effectiveRate = judgement?.finalSuccessRate ?: rule.successRate

        val success = probabilityRoller.succeeds(effectiveRate)
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
                timingGrade = judgement?.grade,
                timingBonusRate = judgement?.bonusRate,
                baseSuccessRate = judgement?.baseSuccessRate,
                finalSuccessRate = judgement?.finalSuccessRate,
            )
        )
        return EvolutionAttemptResult(
            success = success,
            fromLevel = fromLevel,
            resultLevel = evo.level,
            cost = rule.attemptCost,
            timingGrade = judgement?.grade,
            timingBonusRate = judgement?.bonusRate,
            baseSuccessRate = judgement?.baseSuccessRate,
            finalSuccessRate = judgement?.finalSuccessRate,
        )
    }

    private fun EvolutionAttempt.toResult() =
        EvolutionAttemptResult(
            success, fromLevel, resultLevel, cost,
            timingGrade, timingBonusRate, baseSuccessRate, finalSuccessRate,
        )
}
