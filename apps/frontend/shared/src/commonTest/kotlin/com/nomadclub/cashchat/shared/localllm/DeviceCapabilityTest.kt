package com.nomadclub.cashchat.shared.localllm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DeviceCapabilityTest {
    private val spec = DEFAULT_GEMMA_SPEC.copy(
        sizeBytes = 1_000L,
        minRamBytes = 4_000L,
    )

    @Test
    fun `RAM과 저장공간이 충분하면 Ok`() {
        val result = canRunGemma(spec, ramBytes = 8_000L, freeStorageBytes = 5_000L)
        assertEquals(CapabilityResult.Ok, result)
    }

    @Test
    fun `RAM이 부족하면 Insufficient`() {
        val result = canRunGemma(spec, ramBytes = 2_000L, freeStorageBytes = 5_000L)
        assertIs<CapabilityResult.Insufficient>(result)
    }

    @Test
    fun `RAM 측정값이 0이면 차단하지 않는다`() {
        val result = canRunGemma(spec, ramBytes = 0L, freeStorageBytes = 5_000L)
        assertEquals(CapabilityResult.Ok, result)
    }

    @Test
    fun `저장공간은 모델 크기의 1_1배 여유분을 요구한다`() {
        val result = canRunGemma(spec, ramBytes = 8_000L, freeStorageBytes = 1_099L)
        assertIs<CapabilityResult.Insufficient>(result)
    }

    @Test
    fun `저장공간 측정 실패값 0은 통과시키지 않는다`() {
        val result = canRunGemma(spec, ramBytes = 8_000L, freeStorageBytes = 0L)
        assertIs<CapabilityResult.Insufficient>(result)
    }
}
