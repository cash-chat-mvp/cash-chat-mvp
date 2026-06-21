package com.nomadclub.cashchat.shared.invite

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FakeInviteRepositoryTest {

    @Test
    fun `getInviteStatus 는 내 코드와 보상값을 준다`() = runTest {
        val status = FakeInviteRepository().getInviteStatus()
        assertEquals("ABC123", status.myCode)
        assertTrue(status.redeemAvailable)
        assertEquals(500, status.rewardCoin)
        assertEquals(10, status.rewardEnergy)
    }

    @Test
    fun `redeemCode - 유효한 코드면 성공하고 에너지 지급`() = runTest {
        val repo = FakeInviteRepository()
        val result = repo.redeemCode("XYZ789")
        assertTrue(result.success)
        assertEquals(10, result.awardedEnergy)
    }

    @Test
    fun `redeemCode - 성공 후 redeemAvailable 가 false`() = runTest {
        val repo = FakeInviteRepository()
        repo.redeemCode("XYZ789")
        assertFalse(repo.getInviteStatus().redeemAvailable)
    }

    @Test
    fun `redeemCode - 자기 코드면 실패`() = runTest {
        val repo = FakeInviteRepository()
        val result = repo.redeemCode("ABC123")
        assertFalse(result.success)
        assertEquals("본인 코드는 사용할 수 없어요", result.message)
    }

    @Test
    fun `redeemCode - 빈 코드면 실패`() = runTest {
        val result = FakeInviteRepository().redeemCode("   ")
        assertFalse(result.success)
        assertEquals("코드를 입력해주세요", result.message)
    }

    @Test
    fun `redeemCode - 이미 사용했으면 실패`() = runTest {
        val repo = FakeInviteRepository()
        repo.redeemCode("XYZ789")
        val result = repo.redeemCode("QWE456")
        assertFalse(result.success)
        assertEquals("이미 추천 코드를 사용했어요", result.message)
    }
}
