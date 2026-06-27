package com.wnl.cashchat.api.domain.evolution.exception

/** 타이밍 세션이 없거나 만료/타 사용자/변조(releasedAtMs 상한 초과)일 때. 비용 차감 전에 던진다. */
class InvalidTimingSessionException(
    message: String = "Invalid or expired timing session",
) : RuntimeException(message)
