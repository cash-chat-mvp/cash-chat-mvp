package com.nomadclub.cashchat.config

/**
 * 앱 전역 차단 상태. Remote Config 긴급 키([RemoteConfigKeys.MAINTENANCE_MODE],
 * [RemoteConfigKeys.FORCE_UPDATE_MIN_VERSION])로 산출된다.
 */
sealed interface AppGateState {
    /** 정상 — 앱 사용 가능. */
    data object None : AppGateState

    /** 점검 모드 — 전체 차단. */
    data class Maintenance(val message: String) : AppGateState

    /** 강제 업데이트 — 현재 버전 < 최소 요구 버전. */
    data class ForceUpdate(val message: String) : AppGateState
}
