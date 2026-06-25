package com.nomadclub.cashchat.shared.chat

import com.nomadclub.cashchat.shared.chat.model.ChatItem
import com.nomadclub.cashchat.shared.core.network.ApiException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** ChatApi와 같은 시그니처의 가짜. ChatStore는 이 인터페이스에 의존한다. */
private class FakeChatGateway : ChatGateway {
    var streamResult: (suspend () -> Flow<ChatStreamEvent>)? = null
    var createdConversations = 0

    override suspend fun createConversation(title: String?) =
        com.nomadclub.cashchat.shared.chat.model.ConversationDto(
            conversationId = (++createdConversations).toLong(), title = title ?: "새 대화",
            createdAt = "2026-06-10T00:00:00Z", updatedAt = "2026-06-10T00:00:00Z",
        )

    override suspend fun listConversations() = emptyList<com.nomadclub.cashchat.shared.chat.model.ConversationSummaryDto>()
    override suspend fun getMessages(conversationId: Long) = emptyList<com.nomadclub.cashchat.shared.chat.model.ChatMessageDto>()
    override fun streamMessage(conversationId: Long, message: String): Flow<ChatStreamEvent> =
        flow { streamResult!!.invoke().collect { emit(it) } }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatStoreTest {

    private fun TestScope.collectResourceFeedback(
        store: ChatStore,
        into: MutableList<ChatResourceFeedback>,
    ): Job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
        store.resourceFeedback.collect { into += it }
    }

    @Test
    fun `전송 성공 - pending이 confirmed되고 assistant 텍스트가 누적된다`() = runTest {
        val gateway = FakeChatGateway()
        gateway.streamResult = {
            flow {
                emit(ChatStreamEvent.Token("안녕"))
                emit(ChatStreamEvent.Token("하세요"))
                emit(ChatStreamEvent.Done)
            }
        }
        val store = ChatStore(gateway, this)
        store.sendMessage("hi")
        testScheduler.advanceUntilIdle()

        val items = store.items.value
        val user = items.filterIsInstance<ChatItem.UserMessage>().last()
        val assistant = items.filterIsInstance<ChatItem.AssistantMessage>().last()
        assertEquals(ChatItem.SendStatus.CONFIRMED, user.status)
        assertEquals("안녕하세요", assistant.text)
        assertEquals(false, assistant.isStreaming)
    }

    @Test
    fun `첫 토큰에서 에너지 차감 이벤트를 1회만 발행하고 완료 시 보상 이벤트를 발행한다`() = runTest {
        val gateway = FakeChatGateway().apply {
            streamResult = {
                flow {
                    emit(ChatStreamEvent.Token("응"))
                    emit(ChatStreamEvent.Token("답"))
                    emit(ChatStreamEvent.Done)
                }
            }
        }
        val store = ChatStore(gateway, this)
        val events = mutableListOf<ChatResourceFeedback>()
        val job = collectResourceFeedback(store, events)
        testScheduler.runCurrent()

        store.sendMessage("질문")
        testScheduler.advanceUntilIdle()
        job.cancel()

        assertEquals(2, events.size)

        val energySpent = assertIs<ChatResourceFeedback.EnergySpent>(events[0])
        assertEquals(-1, energySpent.amount)
        assertTrue(energySpent.messageId.startsWith("u"))

        val rewardEarned = assertIs<ChatResourceFeedback.RewardEarned>(events[1])
        assertEquals(1, rewardEarned.pointDelta)
        assertEquals(1, rewardEarned.expDelta)
        assertTrue(rewardEarned.messageId.startsWith("a"))
        assertTrue(rewardEarned.eventId > energySpent.eventId)
    }

    @Test
    fun `대화방 없으면 첫 전송 전에 자동 생성한다`() = runTest {
        val gateway = FakeChatGateway()
        gateway.streamResult = { flow { emit(ChatStreamEvent.Done) } }
        val store = ChatStore(gateway, this)
        store.sendMessage("처음 인사")
        testScheduler.advanceUntilIdle()
        assertEquals(1, gateway.createdConversations)
    }

    @Test
    fun `에너지 부족 - pending 유지 + 게이트 이벤트 발행 + 확정 저장 안 함`() = runTest {
        val gateway = FakeChatGateway()
        gateway.streamResult = {
            throw ApiException(ApiException.INSUFFICIENT_ENERGY, "에너지 부족", 409)
        }
        val store = ChatStore(gateway, this)
        store.sendMessage("hi")
        testScheduler.advanceUntilIdle()

        val user = store.items.value.filterIsInstance<ChatItem.UserMessage>().last()
        assertEquals(ChatItem.SendStatus.BLOCKED, user.status)
        assertEquals(true, store.energyGateVisible.value)
        assertTrue(store.items.value.filterIsInstance<ChatItem.AssistantMessage>().isEmpty())
    }

    @Test
    fun `retryBlocked - 막혔던 메시지를 같은 대화방으로 재전송한다`() = runTest {
        val gateway = FakeChatGateway()
        var attempts = 0
        gateway.streamResult = {
            attempts++
            if (attempts == 1) throw ApiException(ApiException.INSUFFICIENT_ENERGY, "x", 409)
            flow { emit(ChatStreamEvent.Token("응답")); emit(ChatStreamEvent.Done) }
        }
        val store = ChatStore(gateway, this)
        store.sendMessage("hi")
        testScheduler.advanceUntilIdle()
        store.retryBlocked()
        testScheduler.advanceUntilIdle()

        assertEquals(2, attempts)
        assertEquals(1, gateway.createdConversations) // 대화방 재사용
        val user = store.items.value.filterIsInstance<ChatItem.UserMessage>().last()
        assertEquals(ChatItem.SendStatus.CONFIRMED, user.status)
    }

    @Test
    fun `스트림 도중 error 이벤트 - 부분 텍스트 유지 + isError 표시`() = runTest {
        val gateway = FakeChatGateway()
        gateway.streamResult = {
            flow { emit(ChatStreamEvent.Token("부분")); emit(ChatStreamEvent.StreamError("stream failed")) }
        }
        val store = ChatStore(gateway, this)
        store.sendMessage("hi")
        testScheduler.advanceUntilIdle()

        val assistant = store.items.value.filterIsInstance<ChatItem.AssistantMessage>().last()
        assertEquals("부분", assistant.text)
        assertIs<ChatItem.AssistantMessage>(assistant)
        assertEquals(true, assistant.isError)
    }

    @Test
    fun `스트림 에러에서는 보상 이벤트를 발행하지 않는다`() = runTest {
        val gateway = FakeChatGateway().apply {
            streamResult = {
                flow {
                    emit(ChatStreamEvent.Token("부분"))
                    emit(ChatStreamEvent.StreamError("stream failed"))
                }
            }
        }
        val store = ChatStore(gateway, this)
        val events = mutableListOf<ChatResourceFeedback>()
        val job = collectResourceFeedback(store, events)
        testScheduler.runCurrent()

        store.sendMessage("hi")
        testScheduler.advanceUntilIdle()
        job.cancel()

        assertEquals(1, events.size)
        assertIs<ChatResourceFeedback.EnergySpent>(events.single())
    }

    @Test
    fun `에너지 부족에서는 차감 이벤트를 발행하지 않는다`() = runTest {
        val gateway = FakeChatGateway().apply {
            streamResult = {
                throw ApiException(ApiException.INSUFFICIENT_ENERGY, "에너지 부족", 409)
            }
        }
        val store = ChatStore(gateway, this)
        val events = mutableListOf<ChatResourceFeedback>()
        val job = collectResourceFeedback(store, events)
        testScheduler.runCurrent()

        store.sendMessage("hi")
        testScheduler.advanceUntilIdle()
        job.cancel()

        assertTrue(events.isEmpty())
    }

    @Test
    fun `reset은 추가 리소스 피드백 이벤트를 발행하지 않는다`() = runTest {
        val gateway = FakeChatGateway().apply {
            streamResult = {
                flow {
                    emit(ChatStreamEvent.Token("응답"))
                    emit(ChatStreamEvent.Done)
                }
            }
        }
        val store = ChatStore(gateway, this)
        val events = mutableListOf<ChatResourceFeedback>()
        val job = collectResourceFeedback(store, events)
        testScheduler.runCurrent()

        store.sendMessage("질문")
        testScheduler.advanceUntilIdle()

        store.reset()
        testScheduler.advanceUntilIdle()
        job.cancel()

        assertEquals(2, events.size)
        assertIs<ChatResourceFeedback.EnergySpent>(events[0])
        assertIs<ChatResourceFeedback.RewardEarned>(events[1])
    }
}
