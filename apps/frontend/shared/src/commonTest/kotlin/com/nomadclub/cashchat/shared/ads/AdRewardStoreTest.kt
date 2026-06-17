package com.nomadclub.cashchat.shared.ads

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AdRewardStoreTest {

    @Test
    fun `폴링 - 보상 적립 횟수가 증가하면 즉시 성공으로 끝난다`() = runTest {
        var calls = 0
        val store = AdRewardStore(
            fetchQuota = {
                calls++
                // 광고 보상이 적립되면 usedToday 가 증가한다.
                if (calls >= 2) AdRewardQuotaDto(4, 10, 6, "2026-06-11T00:00:00+09:00")
                else AdRewardQuotaDto(3, 10, 7, "2026-06-11T00:00:00+09:00")
            },
            issueNonce = { IssueNonceDto("n", "x") },
            scope = this,
            pollDelaysMillis = List(5) { 0L },
        )
        val rewarded = store.awaitRewardApplied(baselineUsedToday = 3)
        assertEquals(true, rewarded)
        assertEquals(2, calls)
    }

    @Test
    fun `폴링 - 적립 횟수가 끝까지 변동 없으면 false를 반환한다`() = runTest {
        var calls = 0
        val store = AdRewardStore(
            fetchQuota = { calls++; AdRewardQuotaDto(3, 10, 7, "2026-06-11T00:00:00+09:00") },
            issueNonce = { IssueNonceDto("n", "x") },
            scope = this,
            pollDelaysMillis = List(5) { 0L },
        )
        val rewarded = store.awaitRewardApplied(baselineUsedToday = 3)
        assertEquals(false, rewarded)
        assertEquals(6, calls)
    }

    @Test
    fun `폴링 - 에너지만 늘고 적립 횟수는 그대로면 패시브 회복으로 보고 false`() = runTest {
        // 광고 보상 없이 패시브 자동회복으로 에너지만 증가한 상황을 보상 성공으로 오인하면 안 된다.
        val store = AdRewardStore(
            fetchQuota = { AdRewardQuotaDto(3, 10, 7, "2026-06-11T00:00:00+09:00") },
            issueNonce = { IssueNonceDto("n", "x") },
            scope = this,
            pollDelaysMillis = List(5) { 0L },
        )
        assertEquals(false, store.awaitRewardApplied(baselineUsedToday = 3))
    }
}
