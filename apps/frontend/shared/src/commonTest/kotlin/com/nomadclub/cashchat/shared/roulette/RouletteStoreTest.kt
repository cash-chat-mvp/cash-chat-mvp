package com.nomadclub.cashchat.shared.roulette

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RouletteStoreTest {

    @Test
    fun `refresh 는 status 를 채운다`() = runTest {
        val store = RouletteStore(FakeRouletteRepository(random = { 0.5 }), onEnergyChanged = {})
        assertEquals(null, store.status.value)
        store.refresh()
        assertEquals(5, store.status.value?.dailyLimit)
        assertEquals(true, store.status.value?.freeSpinAvailable)
    }

    @Test
    fun `spin(무료) 은 결과 반환·에너지 콜백·status 갱신`() = runTest {
        var energyRefreshed = 0
        val store = RouletteStore(FakeRouletteRepository(random = { 0.005 }), onEnergyChanged = { energyRefreshed++ })
        val result = store.spin()
        assertEquals(RoulettePrize.JACKPOT_100, result.prize)
        assertEquals(1, energyRefreshed)
        assertFalse(store.status.value?.freeSpinAvailable ?: true)
        assertEquals(4, store.status.value?.remaining)
    }

    @Test
    fun `prepareAdSpin + spinWithAd - 광고 게이트 스핀이 결과 반환·status 갱신`() = runTest {
        var energyRefreshed = 0
        val store = RouletteStore(FakeRouletteRepository(random = { 0.5 }), onEnergyChanged = { energyRefreshed++ })
        store.spin() // 무료 소진
        store.prepareAdSpin() // nonce 발급(스텁)
        val result = store.spinWithAd()
        assertEquals(RoulettePrize.E3, result.prize)
        assertEquals(2, energyRefreshed)
        assertEquals(2, store.status.value?.spinsUsedToday)
        assertEquals(3, store.status.value?.remaining)
    }
}
