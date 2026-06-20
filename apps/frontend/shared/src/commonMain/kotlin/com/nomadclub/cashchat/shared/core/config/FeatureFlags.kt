package com.nomadclub.cashchat.shared.core.config

/** BE 미구현 기능의 진입점 차단 플래그. API가 배포되면 true로 전환 + 연결 확인. */
object FeatureFlags {
    const val POINT_BALANCE = true         // P1-1 GET /api/points/me (BE 배포 완료)
    const val POINT_TOPUP = false          // P1-2 POST /api/energy/topup
    const val ENERGY_RECOVERY = false      // P1-3 energy/me 확장
    const val CONVERSATION_EDIT = false    // P2-1 삭제·이름변경
    const val COUPANG_CARD = true          // P2-2 SSE product — 수신 시 자동 렌더(플래그는 UI 데모용)
    const val AD_GATE = true               // P2-3 SSE gate — 수신 시 자동 렌더
    const val EVOLUTION_HISTORY = false    // P3-1 attempts 조회
    const val SHARE_LINK = false           // P3-3 공개 공유
}
