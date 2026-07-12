package com.nomadclub.cashchat.shared.session

import com.nomadclub.cashchat.shared.ads.AdRewardStore
import com.nomadclub.cashchat.shared.attendance.AttendanceStore
import com.nomadclub.cashchat.shared.chat.ChatStore
import com.nomadclub.cashchat.shared.evolution.EvolutionStore
import com.nomadclub.cashchat.shared.hud.HudStore
import com.nomadclub.cashchat.shared.localllm.ChatModeStore
import com.nomadclub.cashchat.shared.localllm.LocalChatHistory
import com.nomadclub.cashchat.shared.points.PointsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 로그아웃·세션 만료 시 사용자별 상태를 보유한 공유 싱글톤 스토어를 일괄 초기화한다.
 *
 * 스토어들은 Koin single 로 등록돼 계정 전환 후에도 인스턴스가 유지되므로, 토큰만 지우면
 * 다음 사용자 화면에 이전 사용자의 대화·출석·잔액·진화 상태가 잠깐 노출될 수 있다.
 * 토큰 삭제와 같은 시점에 이 클래스로 모든 스토어를 비운다.
 */
class SessionResetter(
    private val chatStore: ChatStore,
    private val attendanceStore: AttendanceStore,
    private val hudStore: HudStore,
    private val evolutionStore: EvolutionStore,
    private val adRewardStore: AdRewardStore,
    private val pointsRepository: PointsRepository,
    private val chatModeStore: ChatModeStore,
    private val localChatHistory: LocalChatHistory,
    private val scope: CoroutineScope,
) {
    fun reset() {
        chatStore.reset()
        attendanceStore.reset()
        hudStore.reset()
        evolutionStore.reset()
        adRewardStore.reset()
        pointsRepository.reset()
        chatModeStore.reset()
        scope.launch { localChatHistory.clear() }
    }
}
