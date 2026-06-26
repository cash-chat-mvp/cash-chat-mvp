package com.wnl.cashchat.api.domain.evolution.web.request

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * 진화 시도. idempotencyKey 는 클라이언트가 시도마다 새로 생성(UUID 등)하며,
 * 동일 키 재요청은 서버가 같은 결과를 반환한다(이중 차감 방지).
 */
data class EvolutionAttemptRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val idempotencyKey: String,
    @field:Valid
    val timing: TimingAttemptRequest? = null,
)