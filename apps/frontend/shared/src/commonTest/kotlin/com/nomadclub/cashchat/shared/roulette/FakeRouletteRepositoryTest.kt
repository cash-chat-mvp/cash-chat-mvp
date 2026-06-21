package com.nomadclub.cashchat.shared.roulette

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FakeRouletteRepositoryTest {

    @Test
    fun `spin - 보유 스핀이 없으면 예외`() = runTest {
        val repo = FakeRouletteRepository(random = { 0.5 })
        repo.spin() // availableSpins 1 -> 0
        assertFailsWith<IllegalStateException> { repo.spin() }
    }

    @Test
    fun `확률 경계 - 0_005 면 잭팟`() = runTest {
        val repo = FakeRouletteRepository(random = { 0.005 })
        assertEquals(RoulettePrize.JACKPOT_100, repo.spin().prize)
    }

    @Test
    fun `확률 경계 - 0_05 면 E10`() = runTest {
        val repo = FakeRouletteRepository(random = { 0.05 })
        assertEquals(RoulettePrize.E10, repo.spin().prize)
    }

    @Test
    fun `확률 경계 - 0_5 면 E3`() = runTest {
        val repo = FakeRouletteRepository(random = { 0.5 })
        assertEquals(RoulettePrize.E3, repo.spin().prize)
    }

    @Test
    fun `확률 경계 - 0_9 면 꽝`() = runTest {
        val repo = FakeRouletteRepository(random = { 0.9 })
        assertEquals(RoulettePrize.MISS, repo.spin().prize)
    }

    @Test
    fun `spin 은 결과 prize 와 일치하는 segmentIndex 를 돌려준다`() = runTest {
        val repo = FakeRouletteRepository(random = { 0.005 })
        val result = repo.spin()
        val status = repo.getStatus()
        assertEquals(result.prize, status.segments[result.segmentIndex].prize)
    }

    @Test
    fun `spin 은 무료 1회를 소모하고 availableSpins 를 줄인다`() = runTest {
        val repo = FakeRouletteRepository(random = { 0.5 })
        val before = repo.getStatus()
        assertEquals(true, before.freeSpinAvailable)
        assertEquals(1, before.availableSpins)
        repo.spin()
        val after = repo.getStatus()
        assertEquals(false, after.freeSpinAvailable)
        assertEquals(0, after.availableSpins)
        assertEquals(1, after.spinsUsedToday)
    }

    @Test
    fun `awaitSpinCredited 는 availableSpins 를 1 늘리고 adSpinsRemaining 을 줄인다`() = runTest {
        val repo = FakeRouletteRepository(random = { 0.5 })
        repo.spin() // 무료 소모 → availableSpins 0, adSpinsRemaining 4
        val credited = repo.awaitSpinCredited(baselineAvailable = 0)
        assertTrue(credited)
        val after = repo.getStatus()
        assertEquals(1, after.availableSpins)
        assertEquals(3, after.adSpinsRemaining)
    }
}
