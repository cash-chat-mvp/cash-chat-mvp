package com.nomadclub.cashchat.shared.roulette

/** 룰렛 상품(전부 에너지). energy 는 지급 에너지량. */
enum class RoulettePrize(val energy: Int) { JACKPOT_100(100), E10(10), E3(3), MISS(0) }

/** 휠 표시용 칸(고정 배치, 확률과 무관). */
data class RouletteSegment(val index: Int, val prize: RoulettePrize)

/** 룰렛 상태(서버가 진실, 스텁이 모사). */
data class RouletteStatus(
    val dailyLimit: Int,
    val spinsUsedToday: Int,
    val freeSpinAvailable: Boolean,
    val availableSpins: Int,
    val adSpinsRemaining: Int,
    val resetAtKst: String,
    val segments: List<RouletteSegment>,
)

/** 1회 스핀 결과 — UI 는 segmentIndex 칸으로 휠을 멈춘다. */
data class RouletteSpinResult(val prize: RoulettePrize, val segmentIndex: Int, val awardedEnergy: Int)
