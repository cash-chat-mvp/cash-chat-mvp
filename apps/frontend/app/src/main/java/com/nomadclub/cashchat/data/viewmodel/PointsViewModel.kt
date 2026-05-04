package com.nomadclub.cashchat.data.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class PointsViewModel : ViewModel() {

    private val _points = MutableStateFlow(1250)
    val points = _points.asStateFlow()

    private val _messageCount = MutableStateFlow(0)
    val messageCount = _messageCount.asStateFlow()

    fun addPoints(amount: Int) {
        _points.update { it + amount }
    }

    fun spendPoints(amount: Int): Boolean {
        if (_points.value >= amount) {
            _points.update { it - amount }
            return true
        }
        return false
    }

    fun incrementMessageCount() {
        _messageCount.update { it + 1 }
    }
}