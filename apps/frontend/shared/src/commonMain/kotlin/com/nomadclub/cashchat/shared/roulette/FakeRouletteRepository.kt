package com.nomadclub.cashchat.shared.roulette

import kotlin.random.Random

/**
 * 로컬 스텁. 서버 가중 확률(잭팟 1% / E10 10% / E3 70% / 꽝 19%)을 모사한다.
 * @param random 0.0(포함)~1.0(미만) 난수 공급자. 테스트는 고정값 주입.
 * 에너지 실지급은 없음(BE 몫) — UI/애니메이션 검증용.
 */
class FakeRouletteRepository(
    private val random: () -> Double = { Random.nextDouble() },
) : RouletteRepository {

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

    private var status = RouletteStatus(
        dailyLimit = 5,
        spinsUsedToday = 0,
        freeSpinAvailable = true,
        availableSpins = 1,
        adSpinsRemaining = 4,
        resetAtKst = "2026-06-22T00:00:00+09:00",
        segments = segments,
    )

    override suspend fun getStatus(): RouletteStatus = status

    override suspend fun spin(): RouletteSpinResult {
        val r = random()
        val prize = when {
            r < 0.01 -> RoulettePrize.JACKPOT_100
            r < 0.11 -> RoulettePrize.E10
            r < 0.81 -> RoulettePrize.E3
            else -> RoulettePrize.MISS
        }
        val segment = segments.first { it.prize == prize }
        status = status.copy(
            spinsUsedToday = status.spinsUsedToday + 1,
            freeSpinAvailable = false,
            availableSpins = (status.availableSpins - 1).coerceAtLeast(0),
        )
        return RouletteSpinResult(prize, segment.index, prize.energy)
    }

    override suspend fun requestAdSpinNonce(): String = "fake-nonce"

    override suspend fun awaitSpinCredited(baselineAvailable: Int): Boolean {
        if (status.adSpinsRemaining <= 0) return false
        status = status.copy(
            availableSpins = status.availableSpins + 1,
            adSpinsRemaining = status.adSpinsRemaining - 1,
        )
        // 인터페이스 계약대로 baseline 대비 증가 여부로 판정(스텁은 동기 적립이라 항상 +1).
        return status.availableSpins > baselineAvailable
    }
}
