package com.nomadclub.cashchat.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nomadclub.cashchat.feature.chat.models.AdInfo
import com.nomadclub.cashchat.feature.chat.models.ChatMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(listOf(
        ChatMessage.Text(id = "1", text = "안녕하세요! 저는 CashAI 비서예요 🤖\n무엇이든 물어보세요. 대화할수록 포인트도 쌓여요!", isUser = false)
    ))
    val messages = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    
    private var sentCount = 0

    private val adDatabase = listOf(
        AdInfo("배달의민족", "지금 주문하면 3,000원 즉시 할인!", "지금 주문하기", "🍔", 0xFFFFF4E8, 0xFFFF6B00)
    )
    private val defaultAd = AdInfo("CashAI Premium", "광고 없이 AI와 무제한 대화!", "업그레이드", "⭐", 0xFFFFF8E8, 0xFFCC8800)

    fun sendMessage(text: String) {
        viewModelScope.launch {
            val userMessage = ChatMessage.Text(id = System.currentTimeMillis().toString(), text = text, isUser = true)
            _messages.update { it + userMessage }
            sentCount++
            
            _isLoading.value = true
            delay(1500)

            if (sentCount % 3 == 0) {
                 _messages.update { it + ChatMessage.RewardPrompt(id = System.currentTimeMillis().toString() + "-reward") }
            } else {
                val aiResponse = ChatMessage.Text(id = System.currentTimeMillis().toString() + "-ai", text = getAiResponse(text), isUser = false)
                _messages.update { it + aiResponse }
                
                delay(400)
                val ad = getAdForMessage(text)
                _messages.update { it + ChatMessage.InlineAd(id = System.currentTimeMillis().toString() + "-ad", ad = ad) }
            }
            _isLoading.value = false
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