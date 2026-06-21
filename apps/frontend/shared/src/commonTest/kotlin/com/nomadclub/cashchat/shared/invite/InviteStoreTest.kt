package com.nomadclub.cashchat.shared.invite

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InviteStoreTest {

    @Test
    fun `refresh 는 status 를 채운다`() = runTest {
        val store = InviteStore(FakeInviteRepository(), onRewardChanged = {})
        assertEquals(null, store.status.value)
        store.refresh()
        assertEquals("ABC123", store.status.value?.myCode)
    }

    @Test
    fun `redeem 성공 시 보상 콜백 호출·status 갱신`() = runTest {
        var rewardRefreshed = 0
        val store = InviteStore(FakeInviteRepository(), onRewardChanged = { rewardRefreshed++ })
        val result = store.redeem("XYZ789")
        assertTrue(result.success)
        assertEquals(1, rewardRefreshed)
        assertFalse(store.status.value?.redeemAvailable ?: true)
    }

    @Test
    fun `redeem 실패 시 보상 콜백 미호출`() = runTest {
        var rewardRefreshed = 0
        val store = InviteStore(FakeInviteRepository(), onRewardChanged = { rewardRefreshed++ })
        val result = store.redeem("ABC123") // 자기 코드
        assertFalse(result.success)
        assertEquals(0, rewardRefreshed)
    }
}
