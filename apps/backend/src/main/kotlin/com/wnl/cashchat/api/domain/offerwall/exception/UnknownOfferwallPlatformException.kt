package com.wnl.cashchat.api.domain.offerwall.exception

/** 콜백 경로의 {platform} 값이 ANDROID/IOS 중 어느 것에도 해당하지 않을 때. 400 으로 변환된다. */
class UnknownOfferwallPlatformException(val raw: String) :
    RuntimeException("Unknown offerwall platform: $raw")
