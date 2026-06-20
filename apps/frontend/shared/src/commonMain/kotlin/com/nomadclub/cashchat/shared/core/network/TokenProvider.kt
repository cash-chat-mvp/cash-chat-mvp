package com.nomadclub.cashchat.shared.core.network

/** 플랫폼(Android/iOS)이 구현하는 토큰 공급자. shared는 저장 방식을 모른다. */
interface TokenProvider {
    suspend fun accessToken(): String?
    /** 401 수신 시 호출. 갱신 성공 여부 반환. 실패 시 호출측은 로그아웃 플로우로 보낸다. */
    suspend fun refresh(): Boolean
}
