package com.nomadclub.cashchat.shared.roulette

/**
 * 룰렛 데이터 소스. 지금은 FakeRouletteRepository(로컬 스텁), BE 준비 시 RemoteRouletteRepository 로 교체.
 * 스핀 정책: 하루 첫 1회는 무료(spin), 이후는 매 스핀마다 광고 게이트(prepareAdSpin → 광고 → spinWithAd).
 * iOS 에서 호출하므로 suspend 함수는 모두 @Throws.
 */
interface RouletteRepository {
    @Throws(Exception::class) suspend fun getStatus(): RouletteStatus

    /** 무료 첫 스핀. freeSpinAvailable 가 아닐 때 호출하면 예외. */
    @Throws(Exception::class) suspend fun spin(): RouletteSpinResult

    /** 광고 게이트 스핀용 nonce 발급(광고 표시 전). */
    @Throws(Exception::class) suspend fun prepareAdSpin(): String

    /** 광고 시청 후 즉시 스핀. remaining 가 0 이면 예외. (실서버는 nonce SSV 검증 후 처리) */
    @Throws(Exception::class) suspend fun spinWithAd(): RouletteSpinResult
}
