package com.nomadclub.cashchat.shared.ads

/**
 * 채팅 N회마다 네이티브 광고를 삽입할 때의 N(=ad_chat_interval).
 * 값은 플랫폼별 Remote Config(Android AppConfig / iOS AppConfig)에서 주입한다.
 * 0 이하이면 광고 비활성.
 */
fun interface AdChatIntervalProvider {
    fun get(): Long
}
