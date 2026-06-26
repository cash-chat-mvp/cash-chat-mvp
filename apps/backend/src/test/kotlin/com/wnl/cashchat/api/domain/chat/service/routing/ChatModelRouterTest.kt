package com.wnl.cashchat.api.domain.chat.service.routing

import com.wnl.cashchat.api.domain.energy.exception.InsufficientEnergyException
import com.wnl.cashchat.api.domain.energy.service.EnergyService
import com.wnl.cashchat.api.domain.evolution.service.EvolutionService
import com.wnl.cashchat.api.domain.evolution.service.EvolutionStateResult
import com.wnl.cashchat.api.domain.quality.service.QualityPoolService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate

/**
 * TDD 검증 목록:
 * 1. 밥 < 1 → InsufficientEnergyException 전파, 풀·프리미엄 미접근
 * 2. nano 샘플 → NANO 반환, tryConsumePremium 미호출, consume+accrue 호출됨
 * 3. mini 샘플 + 풀 충분(tryConsumePremium=true) → MINI
 * 4. gpt 샘플 + 풀 부족(tryConsumePremium=false) → NANO 강등
 * 5. accrue 는 항상(게이트 통과 시) MEAL_MARGIN 으로 호출, reserve 항상 1회
 */
class ChatModelRouterTest : FunSpec() {

    private val TODAY = LocalDate.of(2026, 6, 6)
    private val USER_ID = 42L
    private val LEVEL = 3
    private val MEAL_MARGIN = 32L
    private val MINI_DELTA = 270L
    private val GPT_DELTA = 1620L

    private lateinit var evolutionService: EvolutionService
    private lateinit var energyService: EnergyService
    private lateinit var qualityPoolService: QualityPoolService
    private lateinit var tierSampler: TierSampler
    private lateinit var router: ChatModelRouter

    private val defaultProps = RoutingProperties(
        mealMarginCentiPt = MEAL_MARGIN,
        miniDeltaCentiPt = MINI_DELTA,
        gptDeltaCentiPt = GPT_DELTA,
    )

    init {
        beforeTest {
            evolutionService = mock()
            energyService = mock()
            qualityPoolService = mock()
            tierSampler = mock()
            router = ChatModelRouter(
                evolutionService = evolutionService,
                energyService = energyService,
                qualityPoolService = qualityPoolService,
                props = defaultProps,
                tierSampler = tierSampler,
            )

            whenever(evolutionService.getState(USER_ID)).thenReturn(
                EvolutionStateResult(level = LEVEL, isMaxLevel = false, nextAttemptCost = null, nextSuccessRate = null)
            )
            whenever(qualityPoolService.throttleScale()).thenReturn(1.0)
        }

        test("밥 부족 시 InsufficientEnergyException 전파, 풀·프리미엄 미접근") {
            whenever(energyService.reserve(USER_ID)).thenThrow(InsufficientEnergyException())

            shouldThrow<InsufficientEnergyException> {
                router.routeAndConsume(USER_ID, TODAY)
            }

            verify(qualityPoolService, never()).accrue(any())
            verify(qualityPoolService, never()).tryConsumePremium(any(), any(), any())
            verify(tierSampler, never()).sample(any(), any())
        }

        test("nano 샘플 → NANO 반환, tryConsumePremium 미호출, reserve+accrue 호출") {
            whenever(tierSampler.sample(any(), any())).thenReturn(ModelTier.NANO)

            val result = router.routeAndConsume(USER_ID, TODAY)

            result shouldBe ModelTier.NANO
            verify(energyService).reserve(USER_ID)
            verify(qualityPoolService).accrue(MEAL_MARGIN)
            verify(qualityPoolService, never()).tryConsumePremium(any(), any(), any())
        }

        test("mini 샘플 + 풀 충분(tryConsumePremium=true) → MINI 반환") {
            whenever(tierSampler.sample(any(), any())).thenReturn(ModelTier.MINI)
            whenever(qualityPoolService.tryConsumePremium(USER_ID, MINI_DELTA, TODAY)).thenReturn(true)

            val result = router.routeAndConsume(USER_ID, TODAY)

            result shouldBe ModelTier.MINI
            verify(energyService).reserve(USER_ID)
            verify(qualityPoolService).accrue(MEAL_MARGIN)
            verify(qualityPoolService).tryConsumePremium(USER_ID, MINI_DELTA, TODAY)
        }

        test("gpt 샘플 + 풀 부족(tryConsumePremium=false) → NANO 강등") {
            whenever(tierSampler.sample(any(), any())).thenReturn(ModelTier.GPT)
            whenever(qualityPoolService.tryConsumePremium(USER_ID, GPT_DELTA, TODAY)).thenReturn(false)

            val result = router.routeAndConsume(USER_ID, TODAY)

            result shouldBe ModelTier.NANO
            verify(energyService).reserve(USER_ID)
            verify(qualityPoolService).accrue(MEAL_MARGIN)
            verify(qualityPoolService).tryConsumePremium(USER_ID, GPT_DELTA, TODAY)
        }

        test("throttleScale 이 scale 로 sampler 에 전달됨 — scale=0.5, level 3 mini=0.20") {
            whenever(qualityPoolService.throttleScale()).thenReturn(0.5)
            whenever(tierSampler.sample(any(), any())).thenReturn(ModelTier.NANO)

            router.routeAndConsume(USER_ID, TODAY)

            // level=3 → mini=0.20, gpt=0.0. scale=0.5 → probMini=0.10, probGpt=0.0
            verify(tierSampler).sample(eq(0.10), eq(0.0))
        }

        test("mini 샘플 + tryConsumePremium 성공 → accrue 정확히 MEAL_MARGIN 으로 1회 호출") {
            whenever(tierSampler.sample(any(), any())).thenReturn(ModelTier.MINI)
            whenever(qualityPoolService.tryConsumePremium(any(), any(), any())).thenReturn(true)

            router.routeAndConsume(USER_ID, TODAY)

            verify(qualityPoolService).accrue(MEAL_MARGIN)
            verify(energyService).reserve(USER_ID)
        }

        test("레벨에 확률 정보가 없을 때 nano 확정 반환, tryConsumePremium 미호출") {
            // 레벨 999 는 RoutingProperties 기본값에 없음
            whenever(evolutionService.getState(USER_ID)).thenReturn(
                EvolutionStateResult(level = 999, isMaxLevel = true, nextAttemptCost = null, nextSuccessRate = null)
            )

            val result = router.routeAndConsume(USER_ID, TODAY)

            result shouldBe ModelTier.NANO
            verify(qualityPoolService, never()).tryConsumePremium(any(), any(), any())
            // sampler 미호출 (확률 없으면 nano 확정)
            verify(tierSampler, never()).sample(any(), any())
        }
    }
}
