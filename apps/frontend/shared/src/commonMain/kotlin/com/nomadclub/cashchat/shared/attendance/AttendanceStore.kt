package com.nomadclub.cashchat.shared.attendance

import com.nomadclub.cashchat.shared.attendance.model.BonusItem
import com.nomadclub.cashchat.shared.attendance.model.RewardPreview
import com.nomadclub.cashchat.shared.points.PointsRepository
import kotlinx.coroutines.CancellationException
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = e.message ?: "출석 정보를 불러오지 못했어요") }
            }
        }
    }

    /** 로그아웃/세션 종료 시 다음 사용자에게 이전 출석 상태가 노출되지 않도록 초기화한다. */
    fun reset() {
        _state.value = AttendanceUiState()
    }

    fun checkIn() {
        if (_state.value.todayChecked || _state.value.isCheckingIn) return
        _state.update { it.copy(isCheckingIn = true, errorMessage = null) }
        scope.launch {
            val result = try {
                service.checkIn()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isCheckingIn = false, errorMessage = e.message ?: "이미 출석했거나 오류가 발생했어요") }
                return@launch
            }
            // 체크인 성공 — 서버가 이미 출석을 기록하고 코인을 적립했다.
            // 코인 적립·보상 이벤트·todayChecked 는 후속 월간 재조회 성공 여부와 무관하게 확정한다.
            // (재조회 실패로 todayChecked 를 되돌리면 사용자가 다시 눌러 중복 체크인 오류만 보게 된다)
            pointsRepository.applyDelta(result.awardedCoin)
            _state.update {
                it.copy(
                    isCheckingIn = false,
                    todayChecked = true,
                    currentStreak = result.streakDayCount,
                    nextReward = result.nextRewardPreview,
                )
            }
            _rewardEvents.emit(CheckInRewardEvent(result.awardedCoin, result.bonusItems))
            // 체크인 응답엔 실제 출석 '날짜'가 없어 달력(checkedDays) 동기화를 위해 월간 정보를 재조회한다.
            // 인자 없이 호출해 서버가 현재 월을 판정하게 한다(이전 _state 의 year/month 는 stale 일 수 있음).
            // best-effort: 실패해도 위 체크인 성공 상태는 유지하고 다음 loadMonthly 에서 보정한다.
            try {
                val monthly = service.getMonthly()
                _state.update { prev ->
                    prev.copy(
                        year = monthly.year,
                        month = monthly.month,
                        todayChecked = monthly.todayChecked,
                        currentStreak = monthly.currentStreak,
                        checkedDays = monthly.checkedDays,
                        nextReward = monthly.nextRewardPreview,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 달력 동기화 실패는 사용자에게 노출하지 않는다(체크인 자체는 성공).
            }
        }
    }
}
