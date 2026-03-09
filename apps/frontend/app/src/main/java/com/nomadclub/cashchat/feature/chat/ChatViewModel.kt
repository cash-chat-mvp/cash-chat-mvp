package com.nomadclub.cashchat.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nomadclub.cashchat.shared.chat.ChatStore

class ChatViewModel : ViewModel() {
    private val store = ChatStore(viewModelScope)

    val messages = store.messages
    val isLoading = store.isLoading

    fun sendMessage(text: String) {
        store.sendMessage(text)
    }
}
