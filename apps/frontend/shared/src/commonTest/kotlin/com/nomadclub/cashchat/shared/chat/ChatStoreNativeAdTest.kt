package com.nomadclub.cashchat.shared.chat

import com.nomadclub.cashchat.shared.ads.AdChatIntervalProvider
import com.nomadclub.cashchat.shared.chat.model.ChatItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class FakeGateway : ChatGateway {
    override suspend fun createConversation(title: String?) =
        com.nomadclub.cashchat.shared.chat.model.ConversationDto(
            conversationId = 1L, title = title ?: "새 대화",
            createdAt = "2026-06-23T00:00:00Z", updatedAt = "2026-06-23T00:00:00Z",
        )
    override suspend fun listConversations() = emptyList<com.nomadclub.cashchat.shared.chat.model.ConversationSummaryDto>()
    override suspend fun getMessages(conversationId: Long) = emptyList<com.nomadclub.cashchat.shared.chat.model.ChatMessageDto>()
    override fun streamMessage(conversationId: Long, message: String): Flow<ChatStreamEvent> =
        flow { emit(ChatStreamEvent.Token("응답")); emit(ChatStreamEvent.Done) }
}

class ChatStoreNativeAdTest {

    private fun store(interval: Long, scope: kotlinx.coroutines.CoroutineScope) =
        ChatStore(FakeGateway(), scope, AdChatIntervalProvider { interval })

    @Test
    fun `interval 3이면 3번째 응답 뒤에 네이티브 광고가 1개 삽입된다`() = runTest {
        val s = store(3, this)
        repeat(3) { s.sendMessage("q$it"); testScheduler.advanceUntilIdle() }
        val ads = s.items.value.filterIsInstance<ChatItem.NativeAd>()
        assertEquals(1, ads.size)
        assertEquals(ChatItem.NativeAd::class, s.items.value.last()::class)
    }

    @Test
    fun `interval 3에서 2번째 응답까지는 광고가 없다`() = runTest {
        val s = store(3, this)
        repeat(2) { s.sendMessage("q$it"); testScheduler.advanceUntilIdle() }
        assertEquals(0, s.items.value.filterIsInstance<ChatItem.NativeAd>().size)
    }

    @Test
    fun `interval 0이면 광고를 삽입하지 않는다`() = runTest {
        val s = store(0, this)
        repeat(5) { s.sendMessage("q$it"); testScheduler.advanceUntilIdle() }
        assertEquals(0, s.items.value.filterIsInstance<ChatItem.NativeAd>().size)
    }

    @Test
    fun `interval 1이면 매 응답마다 광고가 삽입된다`() = runTest {
        val s = store(1, this)
        repeat(3) { s.sendMessage("q$it"); testScheduler.advanceUntilIdle() }
        assertEquals(3, s.items.value.filterIsInstance<ChatItem.NativeAd>().size)
    }

    @Test
    fun `interval 3에서 6번째 응답 뒤에는 네이티브 광고가 2개 삽입된다`() = runTest {
        val s = store(3, this)
        repeat(6) { s.sendMessage("q$it"); testScheduler.advanceUntilIdle() }
        assertEquals(2, s.items.value.filterIsInstance<ChatItem.NativeAd>().size)
    }

    @Test
    fun `대화 전환 후 카운터가 초기화되어 새 대화에서 다시 센다`() = runTest {
        val s = store(3, this)
        repeat(3) { s.sendMessage("q$it"); testScheduler.advanceUntilIdle() }
        s.startNewConversation()
        repeat(3) { s.sendMessage("q$it"); testScheduler.advanceUntilIdle() }
        // 첫 대화 1개 + 둘째 대화 1개. startNewConversation()이 _items를 비우므로
        // 최종 리스트에는 둘째 대화에서 삽입된 광고 1개만 남는다.
        assertEquals(1, s.items.value.filterIsInstance<ChatItem.NativeAd>().size)
    }
}
