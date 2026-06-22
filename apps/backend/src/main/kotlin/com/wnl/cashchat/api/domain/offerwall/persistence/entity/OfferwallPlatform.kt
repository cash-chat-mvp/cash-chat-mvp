package com.wnl.cashchat.api.domain.offerwall.persistence.entity

import com.wnl.cashchat.api.domain.offerwall.exception.UnknownOfferwallPlatformException

/**
 * TNK 오퍼월 앱 플랫폼. TNK 는 안드로이드/iOS 를 별도 앱으로 등록해 앱키·콜백 URL 이 플랫폼마다 다르다.
 * 콜백 경로 /api/offerwall/tnk/callback/{platform} 의 마지막 세그먼트로 식별한다.
 */
enum class OfferwallPlatform {
    ANDROID,
    IOS;

    companion object {
        /** 경로값("android"/"ios", 대소문자 무시)을 enum 으로. 미일치 시 도메인 예외(→ 400). */
        fun from(raw: String): OfferwallPlatform =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
                ?: throw UnknownOfferwallPlatformException(raw)
    }
}
