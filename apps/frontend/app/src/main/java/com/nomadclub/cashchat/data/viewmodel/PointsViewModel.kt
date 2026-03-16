package com.nomadclub.cashchat.data.viewmodel

import androidx.lifecycle.ViewModel
import com.nomadclub.cashchat.shared.points.PointsStore

class PointsViewModel : ViewModel() {
    private val store = PointsStore()

    val points = store.points
    val messageCount = store.messageCount

    fun addPoints(amount: Int) {
        store.addPoints(amount)
    }

    fun spendPoints(amount: Int): Boolean {
        return store.spendPoints(amount)
    }

    fun incrementMessageCount() {
        store.incrementMessageCount()
    }
}
