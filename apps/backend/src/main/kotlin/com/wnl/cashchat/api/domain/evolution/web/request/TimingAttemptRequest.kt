package com.wnl.cashchat.api.domain.evolution.web.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size

data class TimingAttemptRequest(
    @field:NotBlank @field:Size(max = 255) val sessionId: String,
    @field:PositiveOrZero val releasedAtMs: Long,
)
