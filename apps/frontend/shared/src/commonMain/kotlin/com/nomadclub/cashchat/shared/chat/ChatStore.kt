package com.nomadclub.cashchat.shared.chat

import com.nomadclub.cashchat.shared.chat.model.AdInfo
import com.nomadclub.cashchat.shared.chat.model.ChatMessage
import com.nomadclub.cashchat.shared.platform.currentTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ChatStore(
    private val scope: CoroutineScope
) {
    private val _messages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage.Text(
                id = "1",
                text = "안녕하세요! 저는 CashAI 비서예요 🤖\n무엇이든 물어보세요. 대화할수록 포인트도 쌓여요!",
                isUser = false
            )
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val stateMutex = Mutex()
    private var sentCount = 0
    private var inFlightCount = 0

    private val adDatabase = listOf(
        AdInfo("배달의민족", "지금 주문하면 3,000원 즉시 할인!", "지금 주문하기", "🍔", 0xFFFFF4E8, 0xFFFF6B00)
    )
    private val defaultAd = AdInfo("CashAI Premium", "광고 없이 AI와 무제한 대화!", "업그레이드", "⭐", 0xFFFFF8E8, 0xFFCC8800)

    fun sendMessage(text: String) {
        scope.launch {
            val now = currentTimeMillis().toString()
            val userMessage = ChatMessage.Text(id = now, text = text, isUser = true)
            _messages.update { it + userMessage }
            val turn = stateMutex.withLock {
                sentCount += 1
                inFlightCount += 1
                _isLoading.value = inFlightCount > 0
                sentCount
            }

            try {
                delay(1500)

                if (turn % 3 == 0) {
                    _messages.update { it + ChatMessage.RewardPrompt(id = "${currentTimeMillis()}-reward") }
                } else {
                    val aiResponse = ChatMessage.Text(
                        id = "${currentTimeMillis()}-ai",
                        text = getAiResponse(text),
                        isUser = false
                    )
                    _messages.update { it + aiResponse }

                    delay(400)
                    val ad = getAdForMessage(text)
                    _messages.update { it + ChatMessage.InlineAd(id = "${currentTimeMillis()}-ad", ad = ad) }
                }
            } finally {
                stateMutex.withLock {
                    inFlightCount = (inFlightCount - 1).coerceAtLeast(0)
                    _isLoading.value = inFlightCount > 0
                }
            }
        }
    }

    private fun getAiResponse(userMessage: String): String {
        return when {
            userMessage.contains("안녕") -> "안녕하세요! 😊 저는 CashAI 비서예요."
            userMessage.contains("음식") -> "오늘 뭐 드실지 고민이시군요! 😋"
            else -> "'${userMessage.take(15)}...'에 대해 답변 드릴게요! 🤖"
        }
    }

    private fun getAdForMessage(userMessage: String): AdInfo {
        return if (userMessage.contains("음식")) adDatabase[0] else defaultAd
    }
}
