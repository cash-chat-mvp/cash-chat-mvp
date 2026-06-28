package com.nomadclub.cashchat.shared.localllm

import kotlin.test.Test
import kotlin.test.assertEquals

class ChatModeStoreTest {

    @Test
    fun `기본 모드는 CASH_AI 이다`() {
        val store = ChatModeStore()
        assertEquals(ChatModelMode.CASH_AI, store.mode.value)
    }

    @Test
    fun `select 는 현재 모드를 바꾼다`() {
        val store = ChatModeStore()
        store.select(ChatModelMode.GEMMA_LOCAL)
        assertEquals(ChatModelMode.GEMMA_LOCAL, store.mode.value)
    }
}
