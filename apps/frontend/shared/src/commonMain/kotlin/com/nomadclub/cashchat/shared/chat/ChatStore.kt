package com.nomadclub.cashchat.shared.chat

import com.nomadclub.cashchat.shared.chat.model.ChatItem
import com.nomadclub.cashchat.shared.chat.model.ChatMessageDto
import com.nomadclub.cashchat.shared.chat.model.ConversationDto
import com.nomadclub.cashchat.shared.chat.model.ConversationSummaryDto
import com.nomadclub.cashchat.shared.core.network.ApiException
import com.nomadclub.cashchat.shared.platform.currentTimeMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** ChatApi 추상화 — 테스트 대체용. 프로덕션 구현은 ChatApi 위임. */
interface ChatGateway {
    suspend fun createConversation(title: String? = null): ConversationDto
    suspend fun listConversations(): List<ConversationSummaryDto>
    suspend fun getMessages(conversationId: Long): List<ChatMessageDto>
    fun streamMessage(conversationId: Long, message: String): Flow<ChatStreamEvent>
}

class ApiChatGateway(private val api: ChatApi) : ChatGateway {
    override suspend fun createConversation(title: String?) = api.createConversation(title)
    override suspend fun listConversations() = api.listConversations()
    override suspend fun getMessages(conversationId: Long) = api.getMessages(conversationId)
    override fun streamMessage(conversationId: Long, message: String) = api.streamMessage(conversationId, message)
}

/**
 * 채팅 메시지 상태머신 (스펙 §3.1).
 * pending → confirmed(스트림 시작) / blocked(409 에너지 부족 → 게이트).
 */
class ChatStore(
    private val gateway: ChatGateway,
    private val scope: CoroutineScope,
) {
    private val _items = MutableStateFlow<List<ChatItem>>(emptyList())
    val items: StateFlow<List<ChatItem>> = _items.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _energyGateVisible = MutableStateFlow(false)
    val energyGateVisible: StateFlow<Boolean> = _energyGateVisible.asStateFlow()

    /** 스트림 정상 종료 시 1 증가 — HUD가 energy 재조회 트리거로 사용. */
    private val _streamCompletedCount = MutableStateFlow(0)
    val streamCompletedCount: StateFlow<Int> = _streamCompletedCount.asStateFlow()

    /** Ad Gate 정보 (P2-3). gate 이벤트 수신 시 채워지고, 해제 시 null. */
    data class GateInfo(val teaserChars: Int, val rewardCoin: Int)

    private val _gateInfo = MutableStateFlow<GateInfo?>(null)
    val gateInfo: StateFlow<GateInfo?> = _gateInfo.asStateFlow()

    var conversationId: Long? = null
        private set

    private var blockedMessageId: String? = null

    // 진행 중인 스트리밍 Job — 대화 전환/재시도/초기화 시 명시적으로 취소해 백그라운드 누수를 막는다.
    private var streamJob: Job? = null

    @Throws(Exception::class)
    suspend fun openConversation(id: Long) {
        streamJob?.cancel()
        conversationId = id
        val history = gateway.getMessages(id).map { dto ->
            if (dto.role == "USER") {
                ChatItem.UserMessage(id = "m${dto.messageId}", text = dto.content, status = ChatItem.SendStatus.CONFIRMED)
            } else {
                ChatItem.AssistantMessage(id = "m${dto.messageId}", text = dto.content, isStreaming = false)
            }
        }
        _items.value = history
        blockedMessageId = null
        _energyGateVisible.value = false
    }

    fun startNewConversation() {
        streamJob?.cancel()
        conversationId = null
        _items.value = emptyList()
        blockedMessageId = null
        _energyGateVisible.value = false
    }

    /** 로그아웃/세션 종료 시 다음 사용자에게 이전 대화·게이트 상태가 노출되지 않도록 초기화한다. */
    fun reset() {
        streamJob?.cancel()
        conversationId = null
        blockedMessageId = null
        _items.value = emptyList()
        _isStreaming.value = false
        _energyGateVisible.value = false
        _streamCompletedCount.value = 0
        _gateInfo.value = null
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _isStreaming.value) return
        val messageId = "u${currentTimeMillis()}"
        _items.update { it + ChatItem.UserMessage(messageId, trimmed, ChatItem.SendStatus.PENDING) }
        streamJob = scope.launch { stream(messageId, trimmed) }
    }

    /** 게이트에서 충전 완료 후 호출 — 막힌 메시지를 같은 대화방으로 재전송. */
    fun retryBlocked() {
        if (_isStreaming.value) return
        val id = blockedMessageId ?: return
        val message = _items.value.filterIsInstance<ChatItem.UserMessage>().firstOrNull { it.id == id } ?: return
        blockedMessageId = null
        _energyGateVisible.value = false
        updateUser(id) { it.copy(status = ChatItem.SendStatus.PENDING) }
        streamJob = scope.launch { stream(id, message.text) }
    }

    fun dismissEnergyGate() { _energyGateVisible.value = false }

    /** Ad Gate 해제 — 광고 시청 완료 후 호출 (P2-3). */
    fun unlockGatedMessage(messageId: String) {
        _gateInfo.value = null
        updateAssistant(messageId) { it.copy(gated = false) }
    }

    /** 스트림 단절 후 재시도 — 마지막 user 메시지를 재전송. */
    fun retryLastMessage() {
        if (_isStreaming.value) return
        val last = _items.value.filterIsInstance<ChatItem.UserMessage>().lastOrNull() ?: return
        _items.update { items -> items.filterNot { it is ChatItem.AssistantMessage && it.isError } }
        streamJob = scope.launch { stream(last.id, last.text) }
    }

    private suspend fun stream(messageId: String, text: String) {
        _isStreaming.value = true
        // catch 블록에서 기존 스트리밍 메시지를 마감할 수 있도록 try 밖에 선언
        val assistantId = "a${currentTimeMillis()}"
        var assistantAdded = false
        try {
            val convId = conversationId ?: gateway.createConversation(text.take(20)).conversationId.also { conversationId = it }
            gateway.streamMessage(convId, text).collect { event ->
                when (event) {
                    is ChatStreamEvent.Token -> {
                        if (!assistantAdded) {
                            updateUser(messageId) { it.copy(status = ChatItem.SendStatus.CONFIRMED) }
                            _items.update { it + ChatItem.AssistantMessage(assistantId, event.text, isStreaming = true) }
                            assistantAdded = true
                        } else {
                            updateAssistant(assistantId) { it.copy(text = it.text + event.text) }
                        }
                    }
                    is ChatStreamEvent.StreamError -> {
                        updateUser(messageId) { it.copy(status = ChatItem.SendStatus.CONFIRMED) }
                        if (assistantAdded) {
                            updateAssistant(assistantId) { it.copy(isStreaming = false, isError = true) }
                        } else {
                            _items.update { it + ChatItem.AssistantMessage(assistantId, "", isStreaming = false, isError = true) }
                        }
                    }
                    ChatStreamEvent.Done -> {
                        updateUser(messageId) { it.copy(status = ChatItem.SendStatus.CONFIRMED) }
                        if (assistantAdded) updateAssistant(assistantId) { it.copy(isStreaming = false) }
                        _streamCompletedCount.update { it + 1 }
                    }
                    is ChatStreamEvent.ProductCards -> {
                        _items.update { it + ChatItem.ProductCards("p${currentTimeMillis()}", event.products) }
                    }
                    is ChatStreamEvent.Gate -> {
                        _gateInfo.value = GateInfo(event.teaserChars, event.rewardCoin)
                        if (assistantAdded) updateAssistant(assistantId) { it.copy(gated = true) }
                    }
                }
            }
        } catch (e: ApiException) {
            if (e.code == ApiException.INSUFFICIENT_ENERGY) {
                blockedMessageId = messageId
                updateUser(messageId) { it.copy(status = ChatItem.SendStatus.BLOCKED) }
                _energyGateVisible.value = true
            } else if (e.code == ApiException.CONVERSATION_NOT_FOUND) {
                conversationId = null
                updateUser(messageId) { it.copy(status = ChatItem.SendStatus.BLOCKED) }
            } else {
                updateUser(messageId) { it.copy(status = ChatItem.SendStatus.BLOCKED) }
            }
        } catch (e: CancellationException) {
            // 정상 취소(화면 전환/스코프 종료 등)는 에러로 마감하지 않고 그대로 전파한다.
            throw e
        } catch (e: Exception) {
            // 네트워크 단절 등 — 부분 응답 유지 + 기존 메시지를 에러 상태로 마감 (중복 추가 금지)
            if (assistantAdded) {
                updateAssistant(assistantId) { it.copy(isStreaming = false, isError = true) }
            } else {
                _items.update { it + ChatItem.AssistantMessage(assistantId, "", isStreaming = false, isError = true) }
            }
        } finally {
            _isStreaming.value = false
        }
    }

    private fun updateUser(id: String, transform: (ChatItem.UserMessage) -> ChatItem.UserMessage) {
        _items.update { items -> items.map { if (it is ChatItem.UserMessage && it.id == id) transform(it) else it } }
    }

    private fun updateAssistant(id: String, transform: (ChatItem.AssistantMessage) -> ChatItem.AssistantMessage) {
        _items.update { items -> items.map { if (it is ChatItem.AssistantMessage && it.id == id) transform(it) else it } }
    }
}
