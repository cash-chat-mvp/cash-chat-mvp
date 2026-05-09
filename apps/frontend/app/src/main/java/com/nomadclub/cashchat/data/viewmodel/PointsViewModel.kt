package com.nomadclub.cashchat.data.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// TODO(iOS 연동 시점): PointsStore (shared 모듈) 를 주입받아 감싸도록 전환.
//   현재 :shared 모듈이 빌드에서 제외되어 있으므로 로컬 StateFlow로 임시 관리.
//   전환 시 아래 구조로 변경:
//
//   class PointsViewModel(private val store: PointsStore) : ViewModel() {
//       val points = store.points          // shared StateFlow
//       val messageCount = store.messageCount
//       fun addPoints(amount: Int) = store.addPoints(amount)
//       fun spendPoints(amount: Int) = store.spendPoints(amount)
//   }
//
//   NOTE: MainActivity의 rememberSaveable 기반 포인트/메시지 상태도
//         이 ViewModel 하나로 통합 예정 (단일 소스 보장).
class PointsViewModel : ViewModel() {

    // 초기값 0: MainActivity의 rememberSaveable 초기값과 일치시켜 상태 불일치 방지
    private val _points = MutableStateFlow(0)
    val points = _points.asStateFlow()

    private val _messageCount = MutableStateFlow(0)
    val messageCount = _messageCount.asStateFlow()

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