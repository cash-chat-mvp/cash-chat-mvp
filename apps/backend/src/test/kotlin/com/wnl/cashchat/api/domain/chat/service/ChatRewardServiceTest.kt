package com.wnl.cashchat.api.domain.chat.service

import com.wnl.cashchat.api.domain.chat.properties.ChatRewardProperties
import com.wnl.cashchat.api.domain.energy.service.EnergyService
import com.wnl.cashchat.api.domain.evolution.service.EvolutionService
import com.wnl.cashchat.api.domain.point.persistence.entity.PointTransactionReason
import com.wnl.cashchat.api.domain.point.service.UserPointService
import io.kotest.core.spec.style.FunSpec
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

/**
 * TDD 검증 목록:
 * 1. settle → 예약 밥 정산(settleReserved) + cashablePt 적립(CHAT_REWARD, 멱등키 chat:reward:{id}) + 진화 경험치 적립
 * 2. settle 은 환불(refundReserved)을 호출하지 않는다
 * 3. refund → 예약 밥 환불(refundReserved)만 호출, 포인트·경험치 미적립
 */
class ChatRewardServiceTest : FunSpec() {

    private val USER_ID = 7L
    private val ASSISTANT_MESSAGE_ID = 123L

    private lateinit var energyService: EnergyService
    private lateinit var userPointService: UserPointService
    private lateinit var evolutionService: EvolutionService
    private lateinit var service: ChatRewardService

    init {
        beforeTest {
            energyService = mock()
            userPointService = mock()
            evolutionService = mock()
            service = ChatRewardService(
                energyService = energyService,
                userPointService = userPointService,
                evolutionService = evolutionService,
                props = ChatRewardProperties(chatRewardPt = 1L, evolutionExpPerChat = 1L),
            )
        }

        test("settle 는 예약 밥 정산 + cashablePt 적립 + 진화 경험치 적립을 수행한다") {
            service.settle(USER_ID, ASSISTANT_MESSAGE_ID)

            verify(energyService).settleReserved(USER_ID)
            verify(userPointService).recordTransaction(
                userId = USER_ID,
                delta = 1L,
                reason = PointTransactionReason.CHAT_REWARD,
                idempotencyKey = "chat:reward:$ASSISTANT_MESSAGE_ID",
            )
            verify(evolutionService).addExp(USER_ID, 1L)
            verify(energyService, never()).refundReserved(USER_ID)
        }

        test("refund 는 예약 밥 환불만 수행하고 포인트·경험치는 적립하지 않는다") {
            service.refund(USER_ID)

            verify(energyService).refundReserved(USER_ID)
            verify(energyService, never()).settleReserved(USER_ID)
            verify(evolutionService, never()).addExp(any(), any())
        }
    }
}
