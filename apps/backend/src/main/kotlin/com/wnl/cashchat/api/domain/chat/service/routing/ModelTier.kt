package com.wnl.cashchat.api.domain.chat.service.routing

/**
 * 채팅 LLM 모델 등급. 경제 라우터가 결정한다.
 *
 * NANO  — 기본(저비용) 티어. 풀 인출 없음.
 * MINI  — 중간 티어. 공용 풀에서 MINI_DELTA centi-pt 인출.
 * GPT   — 최고 티어. 공용 풀에서 GPT_DELTA centi-pt 인출.
 */
enum class ModelTier {
    NANO,
    MINI,
    GPT,
}
