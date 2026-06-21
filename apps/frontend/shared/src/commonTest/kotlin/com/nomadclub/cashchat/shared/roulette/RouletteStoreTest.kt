package com.nomadclub.cashchat.shared.roulette

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class RouletteStoreTest {

    @Test
    fun `refresh 는 status 를 채운다`() = runTest {
        val store = RouletteStore(FakeRouletteRepository(random = { 0.5 }), onEnergyChanged = {})
        assertEquals(null, store.status.value)
        store.refresh()
        assertEquals(5, store.status.value?.dailyLimit)
    }

    @Test
    fun `spin 은 결과를 반환하고 에너지 변경 콜백을 호출한다`() = runTest {
        var energyRefreshed = 0
        val store = RouletteStore(FakeRouletteRepository(random = { 0.005 }), onEnergyChanged = { energyRefreshed++ })
        val result = store.spin()
        assertEquals(RoulettePrize.JACKPOT_100, result.prize)
        assertEquals(1, energyRefreshed)
        assertEquals(0, store.status.value?.availableSpins)
    }

    @Test
    fun `watchAdForSpin - 미시청이면 false, 크레딧 없음`() = runTest {
        val store = RouletteStore(FakeRouletteRepository(random = { 0.5 }), onEnergyChanged = {})
        store.spin() // availableSpins 0
        val credited = store.watchAdForSpin(showAd = { false })
        assertFalse(credited)
        assertEquals(0, store.status.value?.availableSpins)
    }

    @Test
    fun `watchAdForSpin - 시청하면 크레딧 적립되어 true`() = runTest {
        val store = RouletteStore(FakeRouletteRepository(random = { 0.5 }), onEnergyChanged = {})
        store.spin() // availableSpins 0, adSpinsRemaining 4
        val credited = store.watchAdForSpin(showAd = { true })
        assertTrue(credited)
        assertEquals(1, store.status.value?.availableSpins)
    }

    @Test
    fun `prepareAdSpin + creditAdSpin - iOS 경로로도 크레딧 적립되어 status 갱신`() = runTest {
        val store = RouletteStore(FakeRouletteRepository(random = { 0.5 }), onEnergyChanged = {})
        store.spin() // availableSpins 0
        val baseline = store.status.value?.availableSpins ?: 0
        store.prepareAdSpin() // nonce 발급(스텁)
        val credited = store.creditAdSpin(baseline)
        assertTrue(credited)
        assertEquals(1, store.status.value?.availableSpins)
    }
}
