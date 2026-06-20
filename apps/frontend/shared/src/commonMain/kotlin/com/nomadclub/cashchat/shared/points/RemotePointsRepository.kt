package com.nomadclub.cashchat.shared.points

import com.nomadclub.cashchat.shared.wallet.PointsApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 서버 잔액(GET /api/points/me)을 단일 소스로 사용하는 코인 잔액 저장소.
 *
 * - [refresh] 가 서버 값을 권위 있는(authoritative) 잔액으로 덮어쓴다.
 * - [applyDelta] 는 출석/구매 직후 서버 재조회 전까지의 낙관적(optimistic) UI 갱신용이며,
 *   이후 [refresh] 가 호출되면 서버 값으로 보정된다.
 */
class RemotePointsRepository(
    private val pointsApi: PointsApi,
) : PointsRepository {
    private val _balance = MutableStateFlow(0L)
    override val balance: StateFlow<Long> = _balance.asStateFlow()

    @Throws(Exception::class)
    override suspend fun refresh() {
        try {
            _balance.value = pointsApi.getBalance().balance
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 일시적 오류로 기존 잔액을 0으로 날리지 않도록 유지하되, 디버깅을 위해 로그는 남긴다.
            println("RemotePointsRepository: 잔액 동기화 실패 - ${e.message}")
        }
    }

    override fun applyDelta(delta: Long) = _balance.update { current ->
        // 누적 합산이 Long 범위를 넘어 래핑되어 잔액이 손상되지 않도록 포화(saturating) 덧셈 처리.
        val next = when {
            delta > 0 && current > Long.MAX_VALUE - delta -> Long.MAX_VALUE
            delta < 0 && current < Long.MIN_VALUE - delta -> Long.MIN_VALUE
            else -> current + delta
        }
        next.coerceAtLeast(0)
    }

    override fun reset() { _balance.value = 0 }
}
