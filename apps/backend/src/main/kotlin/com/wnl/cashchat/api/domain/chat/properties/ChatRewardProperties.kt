package com.wnl.cashchat.api.domain.chat.properties

import jakarta.validation.constraints.PositiveOrZero
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * 채팅 완료 보상 정책 (app.chat-reward) — 개정 경제 모델 CC-283 R1.
 *
 * chatRewardPt        — 유효 채팅 1회 완료 시 적립하는 현금성 포인트(cashablePt). 기본 1.
 * evolutionExpPerChat — 유효 채팅 1회 완료 시 적립하는 진화 경험치. 기본 1.
 */
@Validated
@ConfigurationProperties(prefix = "app.chat-reward")
data class ChatRewardProperties(
    @field:PositiveOrZero val chatRewardPt: Long = 1L,
    @field:PositiveOrZero val evolutionExpPerChat: Long = 1L,
)
