package com.nomadclub.cashchat.shared.attendance

import com.nomadclub.cashchat.shared.attendance.model.BonusItem
import com.nomadclub.cashchat.shared.attendance.model.RewardPreview
import com.nomadclub.cashchat.shared.points.PointsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AttendanceUiState(
    val year: Int = 0,
    val month: Int = 0,
    val checkedDays: List<Int> = emptyList(),
    val currentStreak: Int = 0,
    val todayChecked: Boolean = false,
    val nextReward: RewardPreview? = null,
    val isLoading: Boolean = false,
    val isCheckingIn: Boolean = false,
    val errorMessage: String? = null,
)

/** 출석 성공 토스트/애니메이션용 일회성 이벤트. */
data class CheckInRewardEvent(val awardedCoin: Long, val bonusItems: List<BonusItem>)

class AttendanceStore(
    private val service: AttendanceApiService,
    private val pointsRepository: PointsRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(AttendanceUiState())
    val state: StateFlow<AttendanceUiState> = _state.asStateFlow()

    private val _rewardEvents = MutableSharedFlow<CheckInRewardEvent>(extraBufferCapacity = 4)
    val rewardEvents: SharedFlow<CheckInRewardEvent> = _rewardEvents.asSharedFlow()

    fun loadMonthly(year: Int? = null, month: Int? = null) {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        scope.launch {
            try {
                val m = service.getMonthly(year, month)
                _state.update {
                    it.copy(
                        year = m.year, month = m.month, checkedDays = m.checkedDays,
                        currentStreak = m.currentStreak, todayChecked = m.todayChecked,
                        nextReward = m.nextRewardPreview, isLoading = false,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = e.message ?: "출석 정보를 불러오지 못했어요") }
            }
        }
    }

    fun checkIn() {
        if (_state.value.todayChecked || _state.value.isCheckingIn) return
        _state.update { it.copy(isCheckingIn = true, errorMessage = null) }
        scope.launch {
            try {
                val result = service.checkIn()
                pointsRepository.applyDelta(result.awardedCoin)
                _state.update { prev ->
                    val today = prev.checkedDays.maxOrNull()?.plus(1) ?: 1
                    prev.copy(
                        isCheckingIn = false,
                        todayChecked = true,
                        currentStreak = result.streakDayCount,
                        checkedDays = (prev.checkedDays + today).distinct().sorted(),
                        nextReward = result.nextRewardPreview,
                    )
                }
                _rewardEvents.emit(CheckInRewardEvent(result.awardedCoin, result.bonusItems))
            } catch (e: Exception) {
                _state.update { it.copy(isCheckingIn = false, errorMessage = e.message ?: "이미 출석했거나 오류가 발생했어요") }
            }
        }
    }
}
