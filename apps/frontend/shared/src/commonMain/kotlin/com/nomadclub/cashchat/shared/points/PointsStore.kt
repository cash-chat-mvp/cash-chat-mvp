package com.nomadclub.cashchat.shared.points

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class PointsStore(initialPoints: Int = 1250) {
    private val _points = MutableStateFlow(initialPoints)
    val points: StateFlow<Int> = _points.asStateFlow()

    private val _messageCount = MutableStateFlow(0)
    val messageCount: StateFlow<Int> = _messageCount.asStateFlow()

    fun addPoints(amount: Int) {
        if (amount <= 0) return
        _points.update { it + amount }
    }

    fun spendPoints(amount: Int): Boolean {
        if (amount <= 0) return false
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
