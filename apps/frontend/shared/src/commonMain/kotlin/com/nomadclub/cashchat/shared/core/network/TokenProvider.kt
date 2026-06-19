package com.nomadclub.cashchat.shared.core.network

/**
 * 플랫폼별 토큰 저장소 추상화.
 * Android → TokenDataStore(DataStore), iOS → Keychain.
 * suspend 미사용(블로킹 접근 허용) — 기존 TokenDataStore 의 *Blocking 패턴과 정렬.
 */
interface TokenProvider {
    fun accessToken(): String?
    fun refreshToken(): String?
    /** "GUEST" | "MEMBER" | "ADMIN" | null */
    fun role(): String?
    fun deviceToken(): String?
    /** refresh 성공 시 새 토큰 일괄 저장 */
    fun updateTokens(accessToken: String, refreshToken: String)
}
