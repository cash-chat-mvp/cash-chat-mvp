package com.nomadclub.cashchat.shared.roulette

/**
 * 룰렛 데이터 소스. 지금은 FakeRouletteRepository(로컬 스텁), BE 준비 시 RemoteRouletteRepository 로 교체.
 * iOS 에서 호출하므로 suspend 함수는 모두 @Throws.
 */
interface RouletteRepository {
    @Throws(Exception::class) suspend fun getStatus(): RouletteStatus
    @Throws(Exception::class) suspend fun spin(): RouletteSpinResult
    @Throws(Exception::class) suspend fun requestAdSpinNonce(): String
    /** 광고 시청 후 스핀 크레딧 적립을 폴링 판정. baseline 대비 availableSpins 증가 시 true. */
    @Throws(Exception::class) suspend fun awaitSpinCredited(baselineAvailable: Int): Boolean
}
