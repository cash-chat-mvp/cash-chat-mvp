package com.wnl.cashchat.api.domain.chat.service

import com.wnl.cashchat.api.domain.chat.properties.ChatRewardProperties
import com.wnl.cashchat.api.domain.energy.service.EnergyService
import com.wnl.cashchat.api.domain.evolution.service.EvolutionService
import com.wnl.cashchat.api.domain.point.persistence.entity.PointTransactionReason
import com.wnl.cashchat.api.domain.point.service.UserPointService
import org.springframework.stereotype.Service

/**
 * 채팅 완료 보상 루프 (개정 경제 모델 CC-283 R1).
 *
 * 채팅 스트림 진입 시 밥 1개를 예약(reserve)하고, 스트림 종료 시 본 서비스로 정산한다:
 *  - settle(정상 완료): 예약 밥 1개 최종 소진 + 현금성 포인트 적립(멱등키 chat:reward:{assistantMessageId})
 *    + 진화 경험치 적립
 *  - refund(실패/취소): 예약 밥 1개 환불(reserved→available)
 *
 * 별도 @Transactional 을 열지 않아 호출자(ChatService.finalizeAssistantMessage)의 트랜잭션에 합류한다.
 * 호출자의 상태 가드(STREAMING 1회)가 한 채팅당 정확히 1회 정산을 보장하므로,
 * 키-멱등이 아닌 진화 경험치 적립도 이중 적립되지 않는다.
 */
@Service
class ChatRewardService(
    private val energyService: EnergyService,
    private val userPointService: UserPointService,
    private val evolutionService: EvolutionService,
    private val props: ChatRewardProperties,
) {
    /**
     * 정상 완료: 진화 경험치 적립 + 예약 밥 정산 + 현금성 포인트 적립.
     *
     * 락 획득 순서를 user_evolution → user_energy 로 잡는다. EvolutionService.attempt 도
     * 동일하게 user_evolution(findByUserIdForUpdate) → user_energy(applyPostEvolutionBoost) 순으로
     * 잡으므로, 같은 사용자가 채팅 정산과 진화 시도를 동시에 수행해도 ABBA 데드락이 발생하지 않는다.
     */
    fun settle(userId: Long, assistantMessageId: Long) {
        evolutionService.addExp(userId, props.evolutionExpPerChat)
        energyService.settleReserved(userId)
        userPointService.recordTransaction(
            userId = userId,
            delta = props.chatRewardPt,
            reason = PointTransactionReason.CHAT_REWARD,
            idempotencyKey = "chat:reward:$assistantMessageId",
        )
    }

    /** 실패/취소: 예약 밥 환불. */
    fun refund(userId: Long) {
        energyService.refundReserved(userId)
    }
}
