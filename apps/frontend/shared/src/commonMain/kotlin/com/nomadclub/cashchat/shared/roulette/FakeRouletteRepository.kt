package com.nomadclub.cashchat.shared.roulette

import kotlin.random.Random

/**
 * 로컬 스텁. 서버 가중 확률(잭팟 1% / E10 10% / E3 70% / 꽝 19%)을 모사한다.
 * 스핀 정책: 하루 첫 1회 무료(spin), 이후 매 스핀 광고 게이트(spinWithAd). 적립 개념 없음.
 * @param random 0.0(포함)~1.0(미만) 난수 공급자. 테스트는 고정값 주입.
 * 에너지 실지급은 없음(BE 몫) — UI/애니메이션 검증용.
 */
class FakeRouletteRepository(
    private val random: () -> Double = { Random.nextDouble() },
) : RouletteRepository {

    // 누적 확률 경계(서버 가중 확률 모사): 잭팟 1% / +E10 10% / +E3 70% / 나머지 꽝 19%.
    private companion object {
        const val DAILY_LIMIT = 5
        const val P_JACKPOT = 0.01   // 1%
        const val P_E10_CUM = 0.11   // 1 + 10%
        const val P_E3_CUM = 0.81    // 1 + 10 + 70%
    }

    // 표시용 8칸 고정 배치(스펙/BE 문서와 동일): 잭팟1·E10 2·E3 3·꽝 2.
    private val segments = listOf(
        RouletteSegment(0, RoulettePrize.JACKPOT_100),
        RouletteSegment(1, RoulettePrize.E3),
        RouletteSegment(2, RoulettePrize.MISS),
        RouletteSegment(3, RoulettePrize.E10),
        RouletteSegment(4, RoulettePrize.E3),
        RouletteSegment(5, RoulettePrize.MISS),
        RouletteSegment(6, RoulettePrize.E10),
        RouletteSegment(7, RoulettePrize.E3),
    )

    private var spinsUsedToday = 0
    private var freeSpinUsed = false

    private fun status(): RouletteStatus = RouletteStatus(
        dailyLimit = DAILY_LIMIT,
        spinsUsedToday = spinsUsedToday,
        freeSpinAvailable = !freeSpinUsed,
        remaining = (DAILY_LIMIT - spinsUsedToday).coerceAtLeast(0),
        resetAtKst = "2026-06-22T00:00:00+09:00",
        segments = segments,
    )

    override suspend fun getStatus(): RouletteStatus = status()

    /** 무료 첫 스핀. */
    override suspend fun spin(): RouletteSpinResult {
        check(!freeSpinUsed) { "free spin already used" }
        freeSpinUsed = true
        return draw()
    }

    override suspend fun prepareAdSpin(): String = "fake-nonce"

    /** 광고 시청 후 스핀. */
    override suspend fun spinWithAd(): RouletteSpinResult {
        check(DAILY_LIMIT - spinsUsedToday > 0) { "no spins remaining" }
        // 무료를 안 쓰고 광고부터 돌리는 경우도 무료 1회는 소진 처리(첫 스핀은 항상 1회로 카운트).
        freeSpinUsed = true
        return draw()
    }

    private fun draw(): RouletteSpinResult {
        val r = random()
        val prize = when {
            r < P_JACKPOT -> RoulettePrize.JACKPOT_100
            r < P_E10_CUM -> RoulettePrize.E10
            r < P_E3_CUM -> RoulettePrize.E3
            else -> RoulettePrize.MISS
        }
        val segment = segments.first { it.prize == prize }
        spinsUsedToday += 1
        return RouletteSpinResult(prize, segment.index, prize.energy)
    }
}
