package com.nomadclub.cashchat.core.network

import kotlinx.coroutines.sync.Mutex

/**
 * 앱 전역에서 토큰 refresh 를 직렬화하기 위한 단일 락.
 *
 * refresh 경로가 두 개(Retrofit 의 [TokenAuthenticator], Ktor shared 클라이언트)로 나뉘어 있는데
 * 서버가 refresh 토큰을 회전(rotation)시키므로, 두 경로가 같은 refresh 토큰으로 동시에 갱신하면
 * 한쪽만 성공하고 나머지는 401 → 방금 갱신된 세션까지 무효화되는 race 가 생긴다.
 * 두 경로가 **같은** 이 Mutex 를 공유해 refresh 를 한 번에 하나씩만 수행하도록 만든다.
 */
class TokenRefreshGate {
    val mutex = Mutex()
}
