package com.nomadclub.cashchat.shared.di

import com.nomadclub.cashchat.shared.ads.AdRewardStore
import com.nomadclub.cashchat.shared.attendance.AttendanceStore
import com.nomadclub.cashchat.shared.attendance.AttendanceUiState
import com.nomadclub.cashchat.shared.attendance.CheckInRewardEvent
import com.nomadclub.cashchat.shared.chat.ChatStore
import com.nomadclub.cashchat.shared.chat.model.ChatItem
import com.nomadclub.cashchat.shared.evolution.EvolutionStore
import com.nomadclub.cashchat.shared.hud.HudStore
import com.nomadclub.cashchat.shared.points.PointsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Swift 에서 Koin 그래프의 store 인스턴스에 접근하기 위한 헬퍼. */
class KoinHelper : KoinComponent {
    private val chat: ChatStore by inject()
    private val attendance: AttendanceStore by inject()
    private val points: PointsRepository by inject()
    private val hud: HudStore by inject()
    private val evolution: EvolutionStore by inject()
    private val adReward: AdRewardStore by inject()

    fun chatStore(): ChatStore = chat
    fun attendanceStore(): AttendanceStore = attendance
    fun pointsRepository(): PointsRepository = points
    fun hudStore(): HudStore = hud
    fun evolutionStore(): EvolutionStore = evolution
    fun adRewardStore(): AdRewardStore = adReward
}

/**
 * StateFlow/SharedFlow 를 Swift 콜백으로 브리지한다.
 * Swift 는 Flow 를 직접 구독하기 어렵기 때문에 메인 디스패처 스코프에서 collect 후 콜백을 호출한다.
 * Swift ViewModel 의 deinit 에서 cancel() 을 호출하지 않으면 무한 collect 코루틴이 살아남아
 * 메모리 누수가 발생하므로 반드시 취소해야 한다.
 */
class FlowCollector {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun collectAttendance(store: AttendanceStore, onEach: (AttendanceUiState) -> Unit) {
        scope.launch { store.state.collect { onEach(it) } }
    }

    fun collectRewards(store: AttendanceStore, onEach: (CheckInRewardEvent) -> Unit) {
        scope.launch { store.rewardEvents.collect { onEach(it) } }
    }

    fun collectBalance(repo: PointsRepository, onEach: (Long) -> Unit) {
        scope.launch { repo.balance.collect { onEach(it) } }
    }

    fun collectChatItems(store: ChatStore, onEach: (List<ChatItem>) -> Unit) {
        scope.launch { store.items.collect { onEach(it) } }
    }

    fun collectIsStreaming(store: ChatStore, onEach: (Boolean) -> Unit) {
        scope.launch { store.isStreaming.collect { onEach(it) } }
    }

    fun cancel() {
        scope.cancel()
    }
}
