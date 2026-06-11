package com.nomadclub.cashchat.shared.ads

import com.nomadclub.cashchat.shared.energy.EnergyDto
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AdRewardStoreTest {

    @Test
    fun `폴링 - 에너지가 증가하면 즉시 성공으로 끝난다`() = runTest {
        var calls = 0
        val store = AdRewardStore(
            fetchQuota = { AdRewardQuotaDto(3, 10, 7, "2026-06-11T00:00:00+09:00") },
            issueNonce = { IssueNonceDto("n", "x") },
            fetchEnergy = {
                calls++
                if (calls >= 2) EnergyDto(10, 50) else EnergyDto(0, 50)
            },
            scope = this,
            pollDelaysMillis = List(5) { 0L },
        )
        val rewarded = store.awaitRewardApplied(baselineEnergy = 0)
        assertEquals(true, rewarded)
        assertEquals(2, calls)
    }

    @Test
    fun `폴링 - 백오프 전 횟수(6회) 모두 변동 없으면 false를 반환한다`() = runTest {
        var calls = 0
        val store = AdRewardStore(
            fetchQuota = { AdRewardQuotaDto(3, 10, 7, "2026-06-11T00:00:00+09:00") },
            issueNonce = { IssueNonceDto("n", "x") },
            fetchEnergy = { calls++; EnergyDto(0, 50) },
            scope = this,
            pollDelaysMillis = List(5) { 0L },
        )
        val rewarded = store.awaitRewardApplied(baselineEnergy = 0)
        assertEquals(false, rewarded)
        assertEquals(6, calls)
    }
}
