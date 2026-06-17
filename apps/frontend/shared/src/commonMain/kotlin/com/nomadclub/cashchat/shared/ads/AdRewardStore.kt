package com.nomadclub.cashchat.shared.ads

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 광고 보상 플로우 (스펙 §3.2):
 * quota 확인 → nonce 발급 → (UI가 AdMob 표시) → 적립 폴링.
 * AdMob SSV 콜백은 10초 이상 지연이 흔하므로 지수 백오프(2·3·5·8·12초, 총 30초)로 폴링한다.
 * 적립은 서버 재조회 결과로만 반영한다 — 로컬 가산 금지.
 */
class AdRewardStore(
    private val fetchQuota: suspend () -> AdRewardQuotaDto,
    private val issueNonce: suspend () -> IssueNonceDto,
    private val scope: CoroutineScope,
    private val pollDelaysMillis: List<Long> = listOf(2_000, 3_000, 5_000, 8_000, 12_000),
) {
    private val _quota = MutableStateFlow<AdRewardQuotaDto?>(null)
    val quota: StateFlow<AdRewardQuotaDto?> = _quota.asStateFlow()

    @Throws(Exception::class)
    suspend fun refreshQuota(): AdRewardQuotaDto = fetchQuota().also { _quota.value = it }

    @Throws(Exception::class)
    suspend fun requestNonce(): String = issueNonce().nonce

    /**
     * 광고 닫힌 뒤 호출. baseline 대비 **광고 보상 적립 횟수(usedToday)** 증가가 관측되면 true.
     * 에너지 증가가 아니라 quota 의 usedToday 로 판정하는 이유: 에너지는 패시브 자동회복으로도
     * 늘어나므로(자동회복 카운트다운), 광고를 보지 않아도 폴링 구간에 회복 1틱이 끼면 보상 성공으로
     * 오인된다. usedToday 는 서버가 SSV 콜백으로 보상을 적립할 때만 증가하므로 광고 보상만 정확히 격리한다.
     * 즉시 1회 + 백오프 간격마다 1회(총 6회) 조회, 끝까지 변동 없으면 false → UI는 "보상 확인 중" + 수동 새로고침 안내.
     */
    @Throws(Exception::class)
    suspend fun awaitRewardApplied(baselineUsedToday: Int): Boolean {
        repeat(pollDelaysMillis.size + 1) { attempt ->
            if (attempt > 0) delay(pollDelaysMillis[attempt - 1])
            val quota = fetchQuota().also { _quota.value = it }
            if (quota.usedToday > baselineUsedToday) return true
        }
        return false
    }
}
