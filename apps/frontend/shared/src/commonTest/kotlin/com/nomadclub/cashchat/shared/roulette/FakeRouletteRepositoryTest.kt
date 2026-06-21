package com.nomadclub.cashchat.shared.roulette

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FakeRouletteRepositoryTest {

    @Test
    fun `확률 경계 - 0_005 면 잭팟`() = runTest {
        assertEquals(RoulettePrize.JACKPOT_100, FakeRouletteRepository(random = { 0.005 }).spin().prize)
    }

    @Test
    fun `확률 경계 - 0_05 면 E10`() = runTest {
        assertEquals(RoulettePrize.E10, FakeRouletteRepository(random = { 0.05 }).spin().prize)
    }

    @Test
    fun `확률 경계 - 0_5 면 E3`() = runTest {
        assertEquals(RoulettePrize.E3, FakeRouletteRepository(random = { 0.5 }).spin().prize)
    }

    @Test
    fun `확률 경계 - 0_9 면 꽝`() = runTest {
        assertEquals(RoulettePrize.MISS, FakeRouletteRepository(random = { 0.9 }).spin().prize)
    }

    @Test
    fun `spin 은 결과 prize 와 일치하는 segmentIndex 를 돌려준다`() = runTest {
        val repo = FakeRouletteRepository(random = { 0.005 })
        val result = repo.spin()
        assertEquals(result.prize, repo.getStatus().segments[result.segmentIndex].prize)
    }

    @Test
    fun `초기엔 무료 스핀 가능, spin 후 소진되고 remaining 감소`() = runTest {
        val repo = FakeRouletteRepository(random = { 0.5 })
        val before = repo.getStatus()
        assertTrue(before.freeSpinAvailable)
        assertEquals(5, before.remaining)
        repo.spin()
        val after = repo.getStatus()
        assertFalse(after.freeSpinAvailable)
        assertEquals(1, after.spinsUsedToday)
        assertEquals(4, after.remaining)
    }

    @Test
    fun `무료 스핀을 이미 썼으면 spin 은 예외(광고 게이트 사용해야 함)`() = runTest {
        val repo = FakeRouletteRepository(random = { 0.5 })
        repo.spin()
        assertFailsWith<IllegalStateException> { repo.spin() }
    }

    @Test
    fun `spinWithAd 는 remaining 이 있으면 돌아가고 카운트 증가`() = runTest {
        val repo = FakeRouletteRepository(random = { 0.5 })
        repo.spin() // 무료 1회 소진, remaining 4
        repo.spinWithAd()
        val after = repo.getStatus()
        assertEquals(2, after.spinsUsedToday)
        assertEquals(3, after.remaining)
    }

    @Test
    fun `하루 한도(5회) 소진 후 spinWithAd 는 예외`() = runTest {
        val repo = FakeRouletteRepository(random = { 0.5 })
        repo.spin()           // 1
        repeat(4) { repo.spinWithAd() } // 2..5
        assertEquals(0, repo.getStatus().remaining)
        assertFailsWith<IllegalStateException> { repo.spinWithAd() }
    }
}
