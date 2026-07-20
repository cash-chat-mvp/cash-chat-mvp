package com.nomadclub.cashchat.mock

import com.nomadclub.cashchat.shared.points.PointsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakePointsRepo : PointsRepository {
    private val _b = MutableStateFlow(0L)
    override val balance: StateFlow<Long> = _b
    override suspend fun refresh() {}
    override fun applyDelta(delta: Long) { _b.value += delta }
    override fun reset() { _b.value = 0 }
}

class FakeSdkTest {
    @Test
    fun `rewarded ad increments usedToday and fires callbacks`() {
        val state = MockBackendState().apply { usedToday = 0 }
        var rewarded = false; var dismissed = false
        FakeRewardedAdPresenter(state).show(
            activity = org.mockito.Mockito.mock(android.app.Activity::class.java),
            onRewarded = { rewarded = true }, onDismissed = { dismissed = true },
        )
        assertEquals(1, state.usedToday)
        assertTrue(rewarded && dismissed)
    }

    @Test
    fun `offerwall success bumps balance`() = runTest {
        val state = MockBackendState()
        val repo = FakePointsRepo()
        val result = FakeOfferwallLauncher(state, repo)
            .launch(org.mockito.Mockito.mock(android.app.Activity::class.java))
        assertTrue(result.isSuccess)
        assertEquals(1500L, repo.balance.value)
    }

    @Test
    fun `offerwall fail scenario returns failure`() = runTest {
        val state = MockBackendState().apply { scenario = "offerwall_fail" }
        val result = FakeOfferwallLauncher(state, FakePointsRepo())
            .launch(org.mockito.Mockito.mock(android.app.Activity::class.java))
        assertTrue(result.isFailure)
    }
}
