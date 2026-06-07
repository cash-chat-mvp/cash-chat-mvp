package com.nomadclub.cashchat.shared.points

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 코인 잔액 소스. BE 의 GET /api/points/me 미구현 상태를 이 인터페이스 뒤로 격리한다.
 * 준비되면 RemotePointsRepository(httpClient) 로 교체(인터페이스 불변).
 */
interface PointsRepository {
    val balance: StateFlow<Long>
    /** 서버에서 최신 잔액 동기화(준비 전에는 no-op). */
    suspend fun refresh()
    /** 적립 발생 시 로컬 잔액 반영(서버 동기화 전 즉시 UI 갱신용). */
    fun applyDelta(delta: Long)
}

/** BE 부재 동안 사용하는 잠정 구현 — 초기값 + 적립 누적. */
class LocalPointsRepository(initial: Long = 1250) : PointsRepository {
    private val _balance = MutableStateFlow(initial)
    override val balance: StateFlow<Long> = _balance.asStateFlow()
    override suspend fun refresh() { /* no-op until GET /api/points/me 구현 */ }
    override fun applyDelta(delta: Long) = _balance.update { (it + delta).coerceAtLeast(0) }
}
