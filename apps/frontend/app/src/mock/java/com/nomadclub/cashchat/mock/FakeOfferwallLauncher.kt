package com.nomadclub.cashchat.mock

import android.app.Activity
import com.nomadclub.cashchat.offerwall.OfferwallLauncher
import com.nomadclub.cashchat.shared.points.PointsRepository

/**
 * TNK 오퍼월 대체. 성공 시 오퍼 완료 콜백(비동기 SSV) 을 시뮬레이션 —
 * 잔액을 올리고(applyDelta 로 즉시 UI 반영) PointsRepository.refresh() 로 서버 값과 동기화한다.
 * offerwall_fail 시나리오면 실패를 반환(진입 실패 토스트 유도).
 */
class FakeOfferwallLauncher(
    private val state: MockBackendState,
    private val pointsRepository: PointsRepository,
) : OfferwallLauncher {
    override suspend fun launch(activity: Activity): Result<Unit> {
        if (state.scenario == "offerwall_fail") {
            return Result.failure(IllegalStateException("mock offerwall token fail"))
        }
        state.pointsBalance += 1500
        pointsRepository.applyDelta(1500)   // 즉시 UI 반영(서버 동기화 전)
        pointsRepository.refresh()          // /api/points/me 재조회 → balance StateFlow 갱신
        return Result.success(Unit)
    }
}
