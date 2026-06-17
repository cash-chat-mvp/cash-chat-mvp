package com.nomadclub.cashchat.shared.di

import com.nomadclub.cashchat.shared.attendance.AttendanceStore
import com.nomadclub.cashchat.shared.attendance.AttendanceUiState
import com.nomadclub.cashchat.shared.attendance.CheckInRewardEvent
import com.nomadclub.cashchat.shared.core.network.AuthenticatedApiClient
import com.nomadclub.cashchat.shared.points.PointsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Swift 에서 Koin 인스턴스에 접근하기 위한 헬퍼. */
class KoinHelper : KoinComponent {
    private val store: AttendanceStore by inject()
    private val points: PointsRepository by inject()
    private val apiClient: AuthenticatedApiClient by inject()
    fun attendanceStore(): AttendanceStore = store
    fun pointsRepository(): PointsRepository = points

    /** 로그아웃 시 KMM Ktor 클라이언트가 캐시한 BearerTokens 를 비운다(다음 사용자 토큰 재사용 방지). */
    fun clearApiTokenCache() = apiClient.clearTokenCache()
}

/**
 * StateFlow/SharedFlow 를 Swift 콜백으로 브리지.
 * Swift 는 직접 Flow 를 구독하기 어렵기 때문에 메인 디스패처 스코프에서 collect 후 콜백 호출.
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

    /**
     * 구독 중인 모든 collect 코루틴을 취소한다.
     * Swift ViewModel 의 deinit 에서 호출하지 않으면 무한 collect 코루틴이 살아남아 메모리 누수가 발생한다.
     */
    fun cancel() {
        scope.cancel()
    }
}
