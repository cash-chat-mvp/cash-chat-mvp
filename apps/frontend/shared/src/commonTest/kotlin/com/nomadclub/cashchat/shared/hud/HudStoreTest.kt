package com.nomadclub.cashchat.shared.hud

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HudStoreTest {
    @Test
    fun `HudState exp 기본값은 null이다`() {
        val state = HudState()
        assertNull(state.exp)
    }

    @Test
    fun `HudState는 exp 값을 보관한다`() {
        val state = HudState(exp = 3400L)
        assertEquals(3400L, state.exp)
    }
}
