package com.nomadclub.cashchat.shared.roulette

/** 룰렛 상품(전부 에너지). energy 는 지급 에너지량. */
enum class RoulettePrize(val energy: Int) { JACKPOT_100(100), E10(10), E3(3), MISS(0) }

/** 휠 표시용 칸(고정 배치, 확률과 무관). */
data class RouletteSegment(val index: Int, val prize: RoulettePrize)

/**
 * 룰렛 상태(서버가 진실, 스텁이 모사).
 * 스핀 정책: 하루 첫 1회는 무료(freeSpinAvailable), 이후는 매 스핀마다 광고를 봐야 한다(적립 개념 없음).
 */
data class RouletteStatus(
    val dailyLimit: Int,
    val spinsUsedToday: Int,
    /** 하루 첫 무료 스핀 사용 가능 여부. true 면 광고 없이 spin(), false 면 광고 게이트(spinWithAd). */
    val freeSpinAvailable: Boolean,
    /** 오늘 더 돌릴 수 있는 횟수(= dailyLimit - spinsUsedToday). 0 이면 한도 도달. */
    val remaining: Int,
    val resetAtKst: String,
    val segments: List<RouletteSegment>,
)

/** 1회 스핀 결과 — UI 는 segmentIndex 칸으로 휠을 멈춘다. */
data class RouletteSpinResult(val prize: RoulettePrize, val segmentIndex: Int, val awardedEnergy: Int)
