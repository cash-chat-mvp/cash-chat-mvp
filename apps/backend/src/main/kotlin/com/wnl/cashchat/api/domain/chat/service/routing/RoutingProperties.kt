package com.wnl.cashchat.api.domain.chat.service.routing

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * 채팅 모델 라우팅 정책 (app.routing).
 *
 * mealMarginCentiPt  — 채팅 1회당 공용 풀에 적립하는 마진 (centi-pt). 기본 32.
 * miniDeltaCentiPt   — MINI 티어 프리미엄 인출량 (centi-pt). 기본 270.
 * gptDeltaCentiPt    — GPT 티어 프리미엄 인출량 (centi-pt). 기본 1620.
 * levels             — 레벨별 mini/gpt 확률 목록. nano = 1 - mini - gpt.
 *
 * §7.2 기본값: L1(0,0) L2(.05,0) L3(.20,0) L4(.35,.05) L5(.50,.10).
 */
@Validated
@ConfigurationProperties(prefix = "app.routing")
data class RoutingProperties(
    val mealMarginCentiPt: Long = 32L,
    val miniDeltaCentiPt: Long = 270L,
    val gptDeltaCentiPt: Long = 1620L,
    val levels: List<LevelProb> = DEFAULT_LEVELS,
) {
    data class LevelProb(
        val level: Int,
        val mini: Double,
        val gpt: Double,
    ) {
        init {
            require(mini in 0.0..1.0) { "mini probability must be in 0.0..1.0" }
            require(gpt in 0.0..1.0) { "gpt probability must be in 0.0..1.0" }
            require(mini + gpt <= 1.0) { "mini + gpt must be <= 1.0 (nano probability would be negative)" }
        }

        val nano: Double get() = 1.0 - mini - gpt
    }

    fun probFor(level: Int): LevelProb? = levels.firstOrNull { it.level == level }

    companion object {
        val DEFAULT_LEVELS: List<LevelProb> = listOf(
            LevelProb(level = 1, mini = 0.0,  gpt = 0.0),
            LevelProb(level = 2, mini = 0.05, gpt = 0.0),
            LevelProb(level = 3, mini = 0.20, gpt = 0.0),
            LevelProb(level = 4, mini = 0.35, gpt = 0.05),
            LevelProb(level = 5, mini = 0.50, gpt = 0.10),
        )
    }
}
