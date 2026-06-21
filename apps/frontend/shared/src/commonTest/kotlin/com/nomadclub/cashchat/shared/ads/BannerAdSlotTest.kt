package com.nomadclub.cashchat.shared.ads

import com.nomadclub.cashchat.shared.core.config.FeatureFlags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BannerAdSlotTest {

    @Test
    fun `analyticsName 은 슬롯별 소문자 식별자를 반환한다`() {
        assertEquals("chat_top", BannerAdSlot.CHAT_TOP.analyticsName)
        assertEquals("benefit_top", BannerAdSlot.BENEFIT_TOP.analyticsName)
    }

    @Test
    fun `BANNER_ADS 플래그가 켜져 있으면 모든 슬롯이 노출 가능하다`() {
        assertTrue(FeatureFlags.BANNER_ADS)
        BannerAdSlot.entries.forEach { slot ->
            assertEquals(FeatureFlags.BANNER_ADS, slot.isEnabled())
        }
    }
}
